package djapiinterface;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class DjapiInterfaceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DjapiInterfaceApplication.class, args);
    }

}
