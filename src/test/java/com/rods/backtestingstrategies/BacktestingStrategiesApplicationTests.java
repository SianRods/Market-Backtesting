package com.rods.backtestingstrategies;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BacktestingStrategiesApplicationTests extends PostgresIntegrationTest {

    @Test
    void contextLoadsAgainstPostgres() {}
}
