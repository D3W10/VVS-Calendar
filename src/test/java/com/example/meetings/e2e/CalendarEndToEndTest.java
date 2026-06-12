package com.example.meetings.e2e;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:meetings-e2e-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false",
    "app.base-url=http://localhost"
})
class CalendarEndToEndTest {

    @LocalServerPort
    private int port;

    private WebDriver driver;
    private WebDriverWait wait;

    @BeforeEach
    void setUpBrowser() {
        try {
            driver = new SafariDriver();
        } catch (WebDriverException ex) {
            driver = new HtmlUnitDriver(true);
        }

        wait = new WebDriverWait(driver, Duration.ofSeconds(5));
    }

    @AfterEach
    void tearDownBrowser() {
        if (driver != null)
            driver.quit();
    }

    @Test
    void userCanRegisterProposeMeetingAndInviteeCanAcceptIt() {
        register("ana", "ana@example.test", "secret");
        register("bruno", "bruno@example.test", "secret");

        login("ana", "secret");
        waitUntilPageContains("No meetings yet");

        driver.findElement(By.linkText("Propose a meeting")).click();
        wait.until(ExpectedConditions.titleIs("Propose a meeting"));
        driver.findElement(By.id("title")).sendKeys("Planning");
        driver.findElement(By.id("description")).sendKeys("Sprint planning");
        setDateTimeValue("start", "2026-06-10T10:00");
        setDateTimeValue("end", "2026-06-10T11:00");
        driver.findElement(By.id("invitees")).sendKeys("bruno");
        driver.findElement(By.xpath("//button[normalize-space()='Propose']")).click();

        wait.until(ExpectedConditions.urlToBe(baseUrl() + "/calendar"));
        waitUntilPageContains("Planning");
        waitUntilPageContains("tentative");
        waitUntilPageContains("bruno (pending)");
        logout();

        login("bruno", "secret");
        waitUntilPageContains("Pending invites");
        waitUntilPageContains("Planning");
        driver.findElement(By.xpath("//button[normalize-space()='Accept']")).click();

        wait.until(ExpectedConditions.urlToBe(baseUrl() + "/calendar"));
        waitUntilPageContains("Planning");
        waitUntilPageContains("confirmed");
        waitUntilPageContains("ana (accepted)");
        waitUntilPageContains("bruno (accepted)");
        assertThat(driver.getPageSource()).doesNotContain("Pending invites");
    }

    private void register(String username, String email, String password) {
        driver.get(baseUrl() + "/register");
        wait.until(ExpectedConditions.titleIs("Register"));
        driver.findElement(By.id("username")).sendKeys(username);
        driver.findElement(By.id("email")).sendKeys(email);
        driver.findElement(By.id("password")).sendKeys(password);
        driver.findElement(By.xpath("//button[normalize-space()='Register']")).click();
        wait.until(ExpectedConditions.urlContains("/login?registered"));
        waitUntilPageContains("Account created. Please sign in.");
    }

    private void login(String username, String password) {
        driver.get(baseUrl() + "/login");
        wait.until(ExpectedConditions.titleIs("Login"));
        driver.findElement(By.id("username")).sendKeys(username);
        driver.findElement(By.id("password")).sendKeys(password);
        driver.findElement(By.xpath("//button[normalize-space()='Sign in']")).click();
        wait.until(ExpectedConditions.urlToBe(baseUrl() + "/calendar"));
        waitUntilPageContains("Signed in as");
        waitUntilPageContains(username);
    }

    private void logout() {
        WebElement button = driver.findElement(By.xpath("//button[normalize-space()='Sign out']"));
        button.click();
        wait.until(ExpectedConditions.urlContains("/login?logout"));
        waitUntilPageContains("You have been signed out.");
    }

    private void setDateTimeValue(String id, String value) {
        WebElement input = driver.findElement(By.id(id));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        js.executeScript("arguments[0].value = arguments[1]; arguments[0].dispatchEvent(new Event('input'));", input, value);
    }

    private void waitUntilPageContains(String text) {
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("body"), text));
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }
}
