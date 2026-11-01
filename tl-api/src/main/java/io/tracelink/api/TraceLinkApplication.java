package io.tracelink.api;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "io.tracelink")
@MapperScan({"io.tracelink.infra.org.mapper", "io.tracelink.infra.task.mapper", "io.tracelink.infra.audit.mapper"})
public class TraceLinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(TraceLinkApplication.class, args);
    }
}
