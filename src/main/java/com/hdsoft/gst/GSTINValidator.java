package com.hdsoft.gst;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * Looks a GSTIN up on the public GST portal ("Search Taxpayer") and returns the result as JSON.
 * <p>
 * Each call to {@link #validateGSTIN(String)} is a single, fresh request: the GSTIN is first checked offline with
 * {@link GSTINOfflineValidator}, then a headless browser opens the portal, the portal's CAPTCHA is handed to the
 * handler registered with {@link #work(BiConsumer)}, and the result page is read. There are no automatic retries;
 * if a request fails (for example because the CAPTCHA was wrong) make a new call.
 * <p>
 * The result is read from the portal's HTML, so it depends on the portal's current page structure.
 * <p>
 * Register the CAPTCHA handler before the first call. An instance holds no per-request state, so one
 * instance may be used for sequential calls; concurrent calls are safe as long as the handler is.
 */
public class GSTINValidator {

    private static final String GST_SEARCH_URL = "https://services.gst.gov.in/services/searchtp";
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private volatile BiConsumer<BufferedImage, AtomicReference<String>> process;

    /**
     * Creates a validator. Register a CAPTCHA handler with {@link #work(BiConsumer)} before calling
     * {@link #validateGSTIN(String)}.
     */
    public GSTINValidator() {
    }

    /**
     * Validates a GSTIN offline and, if it is well formed, looks it up on the GST portal.
     * <p>
     * The returned JSON always contains {@code "success"}. On success it also contains the taxpayer details
     * read from the portal; on failure it contains {@code "error"} (and {@code "gstin"} when one was supplied).
     * This method does not throw.
     *
     * @param gstin the 15 character GSTIN; surrounding whitespace and letter case are ignored
     * @return a pretty printed JSON string
     */
    public String validateGSTIN(String gstin) {
        JsonObject result = new JsonObject();

        GSTINCheckResult offline = GSTINOfflineValidator.check(gstin);
        if (!offline.isValid()) {
            result.addProperty("success", false);
            if (offline.getGstin() != null) {
                result.addProperty("gstin", offline.getGstin());
            }
            result.addProperty("error", offline.getMessage());
            return gson.toJson(result);
        }

        gstin = offline.getGstin();

        if (process == null) {
            result.addProperty("success", false);
            result.addProperty("gstin", gstin);
            result.addProperty("error", "CAPTCHA handler is not set. Call work(...) before validateGSTIN(...).");
            return gson.toJson(result);
        }

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));

            try {
                BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1280, 900));

                Page page = context.newPage();
                page.setDefaultTimeout(15000);
                page.setDefaultNavigationTimeout(30000);

                page.navigate(GST_SEARCH_URL, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

                Locator gstinInput = page.locator("#for_gstin");
                gstinInput.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));

                // gstinInput.fill(gstin);
                gstinInput.click();
                gstinInput.pressSequentially(gstin, new Locator.PressSequentiallyOptions().setDelay(50));

                Locator captchaInput = page.locator("#fo-captcha");
                Locator captchaImage = page.locator("#imgCaptcha");

                captchaInput.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
                captchaImage.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));

                byte[] captchaBytes = captchaImage.screenshot();

                String captcha = requestCaptcha(captchaBytes);

                if (captcha == null || captcha.trim().isEmpty()) {
                    result.addProperty("success", false);
                    result.addProperty("gstin", gstin);
                    result.addProperty("error", "CAPTCHA entry cancelled.");
                    return gson.toJson(result);
                }

                captcha = captcha.trim();

                if (!captcha.matches("\\d{6}")) {
                    result.addProperty("success", false);
                    result.addProperty("gstin", gstin);
                    result.addProperty("error", "Invalid CAPTCHA. CAPTCHA must contain exactly 6 digits.");
                    return gson.toJson(result);
                }

                captchaInput.fill(captcha);

                Locator searchButton = page.locator("#lotsearch");
                searchButton.click();

                Locator resultContainer = page.locator("#lottable");

                try {
                    resultContainer.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(30000));
                } catch (PlaywrightException e) {
                    String bodyText = page.locator("body").innerText();
                    result.addProperty("success", false);
                    result.addProperty("gstin", gstin);

                    if (bodyText != null && bodyText.toLowerCase().contains("captcha")) {
                        result.addProperty("error", "CAPTCHA validation failed or CAPTCHA expired.");
                    } else {
                        result.addProperty("error", "GST portal did not return a search result.");
                    }
                    return gson.toJson(result);
                }

                JsonObject response = extractResult(page, gstin);
                return gson.toJson(response);
            } finally {
                browser.close();
            }
        } catch (Exception e) {
            result.addProperty("success", false);
            result.addProperty("gstin", gstin);
            result.addProperty("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            return gson.toJson(result);
        }
    }

    /**
     * Extracts GST information from #lottable.
     */
    private JsonObject extractResult(Page page, String gstin) {
        JsonObject json = new JsonObject();
        json.addProperty("success", true);
        json.addProperty("gstin", gstin);

        json.addProperty("legalName", valueByLabel(page, "Legal Name of Business"));
        json.addProperty("tradeName", valueByLabel(page, "Trade Name"));
        json.addProperty("effectiveDate", valueByLabel(page, "Effective Date of registration"));
        json.addProperty("constitutionOfBusiness", valueByLabel(page, "Constitution of Business"));
        json.addProperty("gstinUinStatus", valueByLabel(page, "GSTIN / UIN Status"));
        json.addProperty("taxpayerType", valueByLabel(page, "Taxpayer Type"));

        JsonArray administrativeOffice = extractOfficeList(page, "Administrative Office");
        json.add("administrativeOffice", administrativeOffice);

        JsonArray otherOffice = extractOfficeList(page, "Other Office");
        json.add("otherOffice", otherOffice);

        Locator address = page.locator("#lottable .wordCls");
        if (address.count() > 0) {
            json.addProperty("principalPlaceOfBusiness", address.first().innerText().trim());
        } else {
            json.addProperty("principalPlaceOfBusiness", "");
        }

        Locator coreBusiness = page.locator("#ntcrbs .panel-body span");
        if (coreBusiness.count() > 0) {
            json.addProperty("natureOfCoreBusinessActivity", coreBusiness.first().innerText().trim());
        } else {
            json.addProperty("natureOfCoreBusinessActivity", "");
        }

        JsonArray businessActivities = new JsonArray();
        Locator activities = page.locator("#collapseTwo .panel-body .list-child-inline li");

        for (int i = 0; i < activities.count(); i++) {
            String value = activities.nth(i).innerText().trim();

            value = value.replaceFirst("^\\s*\\d+\\.\\s*", "");
            if (!value.isEmpty()) {
                businessActivities.add(value);
            }
        }
        json.add("natureOfBusinessActivities", businessActivities);

        JsonObject goodsServices = extractGoodsServices(page);
        json.add("goodsAndServices", goodsServices);
        return json;
    }

    /**
     * Finds a value using the <strong> label.
     * <p>
     * Example:
     *
     * <p>
     * <strong>Legal Name of Business</strong>
     * </p>
     * <p>ARIJIT ADITYA</p>
     */
    private String valueByLabel(Page page, String label) {
        Locator labels = page.locator("#lottable strong");
        String expected = normalizeText(label);

        for (int i = 0; i < labels.count(); i++) {
            Locator strong = labels.nth(i);
            String actual = normalizeText(strong.innerText());

            if (expected.equalsIgnoreCase(actual)) {
                Locator parent = strong.locator("xpath=..");
                Locator value = parent.locator("xpath=following-sibling::p[1]");

                if (value.count() > 0) {
                    return value.first().innerText().trim();
                }
            }
        }
        return "";
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private JsonArray extractOfficeList(Page page, String officeName) {
        JsonArray array = new JsonArray();
        Locator columns = page.locator("#lottable .tbl-format .row .inner .col-sm-4");

        for (int i = 0; i < columns.count(); i++) {
            Locator column = columns.nth(i);
            Locator strong = column.locator("strong");

            if (strong.count() == 0) {
                continue;
            }

            String heading = strong.first().innerText().trim();
            if (!officeName.equalsIgnoreCase(heading)) {
                continue;
            }

            Locator items = column.locator("ul.jurisdictList li");

            for (int j = 0; j < items.count(); j++) {
                String value = items.nth(j).innerText().trim();
                if (!value.isEmpty()) {
                    array.add(value);
                }
            }
            break;
        }
        return array;
    }

    private JsonObject extractGoodsServices(Page page) {
        JsonObject result = new JsonObject();
        JsonArray goods = new JsonArray();
        JsonArray services = new JsonArray();

        Locator sections = page.locator("#lottable .table-responsive");

        for (int i = 0; i < sections.count(); i++) {
            Locator section = sections.nth(i);
            Locator heading = section.locator("h4").first();

            if (heading.count() == 0) {
                continue;
            }

            String headingText = heading.innerText().trim();
            if (!headingText.equalsIgnoreCase("Dealing In Goods and Services")) {
                continue;
            }

            Locator table = section.locator("table").first();
            if (table.count() == 0) {
                break;
            }

            Locator rows = table.locator("tbody tr");

            for (int r = 1; r < rows.count(); r++) {
                Locator cells = rows.nth(r).locator("td");
                if (cells.count() < 4) {
                    continue;
                }

                String goodsHsn = cells.nth(0).innerText().trim();
                String goodsDescription = cells.nth(1).innerText().trim();
                String serviceHsn = cells.nth(2).innerText().trim();
                String serviceDescription = cells.nth(3).innerText().trim();

                if (!goodsHsn.isEmpty() || !goodsDescription.isEmpty()) {
                    JsonObject goodsItem = new JsonObject();
                    goodsItem.addProperty("hsn", goodsHsn);
                    goodsItem.addProperty("description", goodsDescription);
                    goods.add(goodsItem);
                }

                if (!serviceHsn.isEmpty() || !serviceDescription.isEmpty()) {
                    JsonObject serviceItem = new JsonObject();
                    serviceItem.addProperty("hsn", serviceHsn);
                    serviceItem.addProperty("description", serviceDescription);
                    services.add(serviceItem);
                }
            }
            break;
        }

        result.add("goods", goods);
        result.add("services", services);

        return result;
    }

    /**
     * Registers the handler that shows the portal's CAPTCHA to a person and returns what they typed.
     * <p>
     * For every lookup the handler is called once, on the calling thread, with the CAPTCHA image and an
     * {@link AtomicReference} to put the answer in. It must block until the answer is available (for example by
     * showing a modal dialog) and leave the reference {@code null} or empty if the user cancels. The portal's
     * CAPTCHA is six digits.
     *
     * @param process the CAPTCHA handler, or {@code null} to remove it
     */
    public void work(BiConsumer<BufferedImage, AtomicReference<String>> process) {
        this.process = process;
    }

    private String requestCaptcha(byte[] captchaBytes) throws IOException {
        final BiConsumer<BufferedImage, AtomicReference<String>> handler = process;
        final AtomicReference<String> result = new AtomicReference<String>();
        final BufferedImage image = ImageIO.read(new ByteArrayInputStream(captchaBytes));

        if (image == null) {
            throw new IOException("The CAPTCHA image could not be read.");
        }

        handler.accept(image, result);

        return result.get();
    }
}