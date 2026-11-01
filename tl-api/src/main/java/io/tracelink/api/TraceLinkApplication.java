package io.tracelink.api;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "io.tracelink")
@EnableScheduling
@MapperScan({"io.tracelink.infra.org.mapper", "io.tracelink.infra.task.mapper", "io.tracelink.infra.audit.mapper",
        "io.tracelink.infra.importing.mapper", "io.tracelink.infra.report.mapper",
        "io.tracelink.infra.exporting.mapper", "io.tracelink.infra.stat.mapper"})
public class TraceLinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(TraceLinkApplication.class, args);
    }
}
