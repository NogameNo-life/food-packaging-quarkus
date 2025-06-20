package org.acme.foodpackaging.bootstrap;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.foodpackaging.domain.*;
import org.acme.foodpackaging.persistence.PackagingScheduleRepository;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.*;
import java.time.*;
import java.util.*;

@ApplicationScoped
public class DemoData24Hours {
    @Inject
    PackagingScheduleRepository repository;

    @ConfigProperty(name = "demo-data.line-count", defaultValue = "6")
    int lineCount;
    @ConfigProperty(name = "demo-data.job-count", defaultValue = "10")
    int jobCount;

    private static final int DEFAULT_PRIORITY = 0;

    final int ALLERGEN_DIFFERENT_GLAZE = 90;
    final int CLEANING_AFTER_ALLERGEN = 240;
    final int CACTUS_CLEANING = 180;
    final int MIN_CLASSIC_GLAZE = 30;
    final int MAX_CLASSIC_GLAZE = 50;
    final int FROM_ROD_TO_CLASSIC = 150;
    final int ROD_DIFFERENT_FILLING = 50;
    final int DIFFERENT_CURD_MASS = 20;
    final int CHANGING_PACKAGING = 10;
    final int TO_NONE_FILLING_ROD = 40;

    private ProductNameShortener shortener;

    private static final Map<String, Boolean> IS_ALLERGEN = Map.of(
            "4810268043727", true,
            "4810268043475", true,
            "4810268054969", true,
            "4810268056826", true
    );

    @Transactional
    public void generateDemoData(@Observes StartupEvent startupEvent) {
        String date = "2025-05-25";
        final LocalDate START_DATE = LocalDate.parse(date);
        final LocalDateTime START_DATE_TIME = LocalDateTime.of(START_DATE, LocalTime.of(8,0));
        final LocalDate END_DATE = START_DATE.plusDays(1);
        final LocalDateTime END_DATE_TIME = LocalDateTime.of(END_DATE, LocalTime.of(4,0));

        PackagingSchedule solution = new PackagingSchedule();
        DurationProvider provider = new DurationProvider();
        this.shortener = new ProductNameShortener();

        solution.setWorkCalendar(new WorkCalendar(START_DATE, END_DATE));

        // Инициализация линий
        List<Line> lines = createLines(lineCount, START_DATE_TIME);

        // Загрузка продуктов и заданий из БД
        Map<String, Product> productMap = new HashMap<>();
        List<Product> products = new ArrayList<>();
        List<Job> jobs = new ArrayList<>();

        String url = "jdbc:sqlserver://10.164.30.246;databaseName=MES;integratedSecurity=true;encrypt=true;trustServerCertificate=true;";

        String sqlQuery = "SELECT v.KSK, v.SNPZ, v.DTI, v.DTM, v.KMC, v.EMK, v.KOLMV, v.MASSA, v.KOLEV, v.NP, v.UX, "
                + "m.MASSA, m.EAN13, m.SNM, m.NAME "
                + "FROM [MES].[dbo].[BD_VZPMC] as v, NS_MC as m "
                + "WHERE (v.KMC = m.KMC) AND (v.DTI = ?) AND (v.KSK = ?) AND (m.MASSA < ?) "
                + "ORDER BY v.SNPZ";

        try {
            // Установка соединения
            try (Connection connection = DriverManager.getConnection(url);
                 PreparedStatement preparedStatement = connection.prepareStatement(sqlQuery)) {

                // Установка параметров для SQL-запроса
                preparedStatement.setString(1, date + "T00:00:00");     // Параметр для v.DTI
                preparedStatement.setString(2, "0119030000");          // Параметр для v.KSK
                preparedStatement.setDouble(3, 0.1);                  // Параметр для m.MASSA

                int id=0;
                int duration = 0;
                // Выполнение запроса
                try (ResultSet resultSet = preparedStatement.executeQuery()) {

                    // Обработка результата

                    while (resultSet.next()) {
                        String snpz = resultSet.getString("SNPZ");
                        String ksk = resultSet.getString("KSK");
                        String dti = resultSet.getString("DTI");
                        String dtm = resultSet.getString("DTM");
                        String kmc = resultSet.getString("KMC");
                        String emk = resultSet.getString("EMK");
                        int kolmv = resultSet.getInt("KOLMV");
                        String vb = resultSet.getString("MASSA"); // MASSA из таблицы BD_VZPMC
                        int quantity= resultSet.getInt("KOLEV");
                        String np = resultSet.getString("NP");
                        String priority = resultSet.getString("UX");
                        double massaM = resultSet.getDouble("MASSA"); // MASSA из таблицы NS_MC
                        String ean13 = resultSet.getString("EAN13");
                        String snm = resultSet.getString("SNM");
                        String name = resultSet.getString("NAME");

                        Product product = productMap.get(ean13);
                        if (product == null) {
                            product = createProduct(ean13, name);
                            productMap.put(ean13, product);
                            products.add(product); // Добавляем только один раз
                        }
                        if(product.getType() == ProductType.ROD){

                            duration = quantity/198 + 4;
                        }
                        else if(product.getType() == ProductType.CACTUS){
                            duration = quantity/184;
                        }
                        else if(product.getType() == ProductType.PLUSH){
                            duration = quantity/164;
                        }
                        // Создание задания
                        Job job = createJob(
                                String.valueOf(++id),
                                np,
                                product,
                                quantity,
                                duration,
                                provider,
                                DEFAULT_PRIORITY,
                                START_DATE_TIME
                        );
                        jobs.add(job);
                    }
                }
            }

        } catch (SQLException e) {
            System.err.println(e.getMessage()); // Выводим стек ошибок для отладки
        }

        // Инициализация времени очистки
        initCleaningDurations(products);

        solution.setLines(lines);
        solution.setProducts(products);
        jobs.sort(Comparator.comparing(Job::getName));
        solution.setJobs(jobs);

        repository.write(solution);
    }

    private Product createProduct(String id, String name) {
        ProductType type = determineProductType(name);
        return new Product(id, name, type, IS_ALLERGEN.getOrDefault(id, false));
    }

    private void initCleaningDurations(List<Product> products) {
        Random random = new Random();

        for (Product currentProduct : products) {
            Map<Product, Duration> cleaningDurationMap = new HashMap<>(products.size());
            Map<Product, Integer> cleaningPenaltyMap = new HashMap<>(products.size());

            for (Product previousProduct : products) {
                Duration cleaningDuration;
                Integer cleaningPenalty;

                // 1. Одинаковый продукт → без чистки
                if (currentProduct.getId().equals(previousProduct.getId())) {
                    cleaningDuration = Duration.ZERO;
                    cleaningPenalty = 0;
                }
                // 2. Один из продуктов — CACTUS → всегда 3 часа
                else if (currentProduct.getType() == ProductType.CACTUS && previousProduct.getType() != ProductType.CACTUS
                        || currentProduct.getType() != ProductType.CACTUS && previousProduct.getType() == ProductType.CACTUS) {
                    cleaningDuration = Duration.ofMinutes(CACTUS_CLEANING);
                    cleaningPenalty = CACTUS_CLEANING;
                }
                // 3. Предыдущий аллерген, текущий — нет
                else if (previousProduct.is_allergen() && !currentProduct.is_allergen()) {
                    cleaningDuration = Duration.ofMinutes(CLEANING_AFTER_ALLERGEN);
                    cleaningPenalty = CLEANING_AFTER_ALLERGEN;
                }
                // 4. Текущий CLASSIC, предыдущий ROD
                else if (currentProduct.getType() == ProductType.CLASSIC
                        && previousProduct.getType() == ProductType.ROD) {
                    cleaningDuration = Duration.ofMinutes(FROM_ROD_TO_CLASSIC);
                    cleaningPenalty = FROM_ROD_TO_CLASSIC;
                }
                // 5. Текущий ROD без начинки, предыдущий ROD с начинкой
                else if(currentProduct.getType() == ProductType.ROD
                        && previousProduct.getType() == ProductType.ROD
                        && currentProduct.getFilling() == FillingType.NONE){
                    cleaningDuration = Duration.ofMinutes(TO_NONE_FILLING_ROD);
                    cleaningPenalty = TO_NONE_FILLING_ROD;
                }
                // 6. Оба ROD, разные глазури
                else if (currentProduct.getType() == ProductType.ROD
                        && previousProduct.getType() == ProductType.ROD
                        && !Objects.equals(previousProduct.getId(), currentProduct.getId())
                        && previousProduct.getGlaze().equals(currentProduct.getGlaze())
                        && previousProduct.getFilling().equals(currentProduct.getFilling()
                )
                ) {
                    cleaningDuration = Duration.ofMinutes(CHANGING_PACKAGING);
                    cleaningPenalty = CHANGING_PACKAGING;
                }
                // 7. Оба ROD, разные начинки
                else if (currentProduct.getType() == ProductType.ROD
                        && previousProduct.getType() == ProductType.ROD
                        && !previousProduct.getGlaze().equals(GlazeType.C65_47)) {
                    cleaningDuration = Duration.ofMinutes(ROD_DIFFERENT_FILLING);
                    cleaningPenalty = ROD_DIFFERENT_FILLING;
                }

                // 8. Оба аллергены, разные глазури
                else if (currentProduct.is_allergen() && previousProduct.is_allergen()
                        && currentProduct.getType() == ProductType.CLASSIC
                        && previousProduct.getType() == ProductType.CLASSIC
                        && !currentProduct.getGlaze().equals(previousProduct.getGlaze())) {
                    cleaningDuration = Duration.ofMinutes(ALLERGEN_DIFFERENT_GLAZE);
                    cleaningPenalty = ALLERGEN_DIFFERENT_GLAZE;
                }
                // 9. Текущий аллерген, предыдущий — нет
                else if (!currentProduct.is_allergen() && previousProduct.is_allergen()) {
                    cleaningDuration = Duration.ofMinutes(CLEANING_AFTER_ALLERGEN);
                    cleaningPenalty = CLEANING_AFTER_ALLERGEN;
                }
                // 10. Оба CLASSIC, разные глазури
                else if (currentProduct.getType() == ProductType.CLASSIC
                        && previousProduct.getType() == ProductType.CLASSIC
                        && !currentProduct.getGlaze().equals(previousProduct.getGlaze())) {
                    int minutes = MIN_CLASSIC_GLAZE + random.nextInt(MAX_CLASSIC_GLAZE - MIN_CLASSIC_GLAZE);
                    cleaningDuration = Duration.ofMinutes(minutes);
                    cleaningPenalty = minutes;
                }
                // 11. Одинаковый тип и глазурь, но разные ID
                else if (currentProduct.getType() == previousProduct.getType()
                        && currentProduct.getGlaze().equals(previousProduct.getGlaze())
                        && !currentProduct.getId().equals(previousProduct.getId())) {
                    cleaningDuration = Duration.ofMinutes(DIFFERENT_CURD_MASS);
                    cleaningPenalty = DIFFERENT_CURD_MASS;
                }
                // 12. По умолчанию
                else {
                    cleaningDuration = Duration.ofMinutes(MAX_CLASSIC_GLAZE);
                    cleaningPenalty = MAX_CLASSIC_GLAZE;
                }

                cleaningDurationMap.put(previousProduct, cleaningDuration);
                cleaningPenaltyMap.put(previousProduct, cleaningPenalty);
            }

            currentProduct.setCleaningDurations(cleaningDurationMap);
            currentProduct.setCleaningPenalties(cleaningPenaltyMap);
        }

    }

    private List<Line> createLines(int lineCount, LocalDateTime startDateTime){

        List<Line> lines = new ArrayList<>(lineCount);
        for(int i=1; i<=lineCount; ++i){
            String lineName = "Line" + String.valueOf(i);
            String operatorName = "Operator" + String.valueOf(i);
            Line line = new Line(String.valueOf(i), lineName, operatorName,startDateTime);
            lines.add(line);
        }
        return lines;
    }
    private ProductType determineProductType(String productName) {
        String lowerName = productName.toLowerCase();

        if (containsAll(lowerName, "творобушки", "флоупак")) return ProductType.ROD;
        if (containsAll(lowerName, "топ", "флоупак")) return ProductType.ROD;
        if (containsAll(lowerName, "фольга")) return ProductType.PLUSH;
        if (containsAll(lowerName, "кактус")) return ProductType.CACTUS;
        return ProductType.CLASSIC;
    }

    private boolean containsAll(String text, String... keywords) {
        for (String kw : keywords) {
            if (!text.contains(kw)) return false;
        }
        return true;
    }

    private Job createJob(String id, String np, Product product, int quantity, int duration, DurationProvider provider, int priority, LocalDateTime startDate) {
        String jobName = shortener.getShortName(product.getId(), product.getName());
        return new Job(
                id,
                jobName,
                np,
                product,
                quantity,
                Duration.ofMinutes(duration),
                provider,
                startDate,
                startDate.plusDays(1).withHour(2).withMinute(0), // Идеальное время завершения
                startDate.plusDays(1).withHour(4).withMinute(0), // Максимальное время завершения
                priority,
                false
        );
    }
}

