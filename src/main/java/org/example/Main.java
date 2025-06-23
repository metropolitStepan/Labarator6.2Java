package org.example;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
    private static final String API_URL = "https://api.hh.ru/vacancies";
    private static final int PAGE_SIZE = 100;


    public static void main(String[] args) {
        try {
            var httpClient = HttpClient.newHttpClient();
            var mapper = new ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            Map<String, DoubleSummaryStatistics> statsByArea = new HashMap<>();
            int page = 0;

            // Проходим по всем страницам результатов
            while (true) {
                var vacancies = fetchVacancies(httpClient, mapper, page);
                if (vacancies == null || vacancies.getItems().isEmpty()) {
                    break;
                }
                aggregateSalaries(vacancies.getItems(), statsByArea);
                page++;
                if (page >= vacancies.getPages()) {
                    break;
                }
            }
            printResults(statsByArea);
        } catch (IOException | InterruptedException e) {
            System.err.println("Ошибка при работе с API: " + e.getMessage());
        }
    }


    // ищем вакансии по указанной странице
    private static HHResponse fetchVacancies(HttpClient client, ObjectMapper mapper, int page)
            throws IOException, InterruptedException {
        String uri = String.format("%s?text=java&per_page=%d&page=%d",
                API_URL, PAGE_SIZE, page);
        var request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .GET()
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return mapper.readValue(response.body(), HHResponse.class);
    }


    // добавляем данные о зп в регионы
    private static void aggregateSalaries(List<Vacancy> items,
                                          Map<String, DoubleSummaryStatistics> stats) {
        for (Vacancy v : items) {
            var sal = v.getSalary();
            if (sal == null || !"RUR".equalsIgnoreCase(sal.getCurrency())) {
                continue;
            }
            double average = computeSalary(sal.getFrom(), sal.getTo());
            stats
                    .computeIfAbsent(v.getArea().getName(), k -> new DoubleSummaryStatistics())
                    .accept(average);
        }
    }


    // средняя зп
    private static double computeSalary(Double from, Double to) {
        if (from != null && to != null) {
            return (from + to) / 2;
        }
        return from != null ? from : (to != null ? to : 0);
    }


    // отсортировываем по зарплатам
    private static void printResults(Map<String, DoubleSummaryStatistics> stats) {
        System.out.println("Регион — Средняя зарплата (RUR)");
        stats.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue().getAverage(), e1.getValue().getAverage()))
                .forEach(entry -> System.out.printf(
                        "%s — %.0f%n", entry.getKey(), entry.getValue().getAverage()));
    }


    // контейнер классов
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class HHResponse {
        private int pages;
        private List<Vacancy> items;

        public int getPages() { return pages; }
        public void setPages(int pages) { this.pages = pages; }
        public List<Vacancy> getItems() { return items; }
        public void setItems(List<Vacancy> items) { this.items = items; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Vacancy {
        private Salary salary;
        private Area area;

        public Salary getSalary() { return salary; }
        public void setSalary(Salary salary) { this.salary = salary; }
        public Area getArea() { return area; }
        public void setArea(Area area) { this.area = area; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Salary {
        private Double from;
        private Double to;
        private String currency;

        public Double getFrom() { return from; }
        public void setFrom(Double from) { this.from = from; }
        public Double getTo() { return to; }
        public void setTo(Double to) { this.to = to; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Area {
        private String name;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
