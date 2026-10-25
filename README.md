# Hdsoft GSTIN Validator

A small Java 8 library for validating Indian **GSTIN** numbers.

It offers two levels of validation:

| Level | Class | Needs network / browser | What it proves |
|-------|-------|-------------------------|----------------|
| **Offline** | `GSTINOfflineValidator` | No | The number is well formed: correct layout, a known state code and a correct check digit. |
| **Portal lookup** | `GSTINValidator` | Yes (headless Chromium) | The GSTIN exists on the GST portal, with the taxpayer's registration details returned as JSON. |

`GSTINValidator` always runs the offline checks first, so a mistyped GSTIN is rejected immediately, before a
browser is started or a CAPTCHA is shown.

## Requirements

- **Java 8** (the library is compiled for, and restricted to, the Java 1.8 language level and API).
- Maven 3.6.3+ to build.
- For portal lookups only: network access to `services.gst.gov.in`. Playwright downloads a Chromium build the
  first time it runs, and on Linux the usual Chromium system libraries must be present.

The offline validator has no dependency on Playwright being usable at runtime.

## Installation

The library is not published to a public repository yet. Build it and install it into your local Maven repository:

```bash
git clone https://github.com/git-hdsoftsolutions/hdsoft-gst-validator.git
cd hdsoft-gst-validator
mvn clean install
```

Then add it to your project:

```xml
<dependency>
    <groupId>com.hdsoft.gst</groupId>
    <artifactId>gstin-validator</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Quick start: offline validation

```java
import com.hdsoft.gst.GSTINCheckResult;
import com.hdsoft.gst.GSTINOfflineValidator;

public class OfflineExample {
    public static void main(String[] args) {
        // Simple yes / no
        boolean ok = GSTINOfflineValidator.isValid("27AAAAA0000A1Z2");   // true

        // Full detail
        GSTINCheckResult result = GSTINOfflineValidator.check(" 27aaaaa0000a1z2 ");
        if (result.isValid()) {
            System.out.println(result.getGstin());       // 27AAAAA0000A1Z2  (trimmed, upper-cased)
            System.out.println(result.getStateCode());   // 27
            System.out.println(result.getStateName());   // Maharashtra
            System.out.println(result.getPan());         // AAAAA0000A
        } else {
            System.out.println(result.getError());       // e.g. INVALID_CHECK_DIGIT
            System.out.println(result.getMessage());     // e.g. Invalid GSTIN check digit.
        }
    }
}
```

`check` never throws and never returns `null`. Input is trimmed and upper-cased, using `Locale.ROOT`, so the
result does not depend on the JVM's default locale.

### Failure reasons

| `GSTINCheckResult.Error` | Message | Meaning |
|--------------------------|---------|---------|
| `EMPTY` | `GSTIN cannot be empty.` | `null` or blank input. |
| `INVALID_FORMAT` | `Invalid GSTIN format.` | Not 15 characters in the standard layout. |
| `INVALID_STATE_CODE` | `Invalid GSTIN state code.` | The first two digits are not a GST state / jurisdiction code. |
| `INVALID_CHECK_DIGIT` | `Invalid GSTIN check digit.` | The 15th character does not match the checksum of the first 14. |

Components are filled in as far as the check got. For example a result that failed on the check digit still
exposes the state and PAN, while a result that failed on the format exposes none of them.

### Also available

```java
char check = GSTINOfflineValidator.computeCheckDigit("27AAAAA0000A1Z");  // '2'
String name = GSTINOfflineValidator.getStateName("29");                  // Karnataka
```

## Portal lookup

`GSTINValidator` opens the GST portal's *Search Taxpayer* page in headless Chromium, enters the GSTIN, and
**passes the portal's CAPTCHA image to a handler you provide**. The handler shows the image to a person and
returns the six digits they type. The library does not solve CAPTCHAs.

```java
import com.hdsoft.gst.GSTINValidator;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;

public class LookupExample {

    public static void main(String[] args) {
        GSTINValidator validator = new GSTINValidator();

        // Called once per lookup, on the calling thread. It must block until the answer is available.
        validator.work((image, answer) -> {
            Runnable ask = () -> {
                JPanel panel = new JPanel(new BorderLayout(10, 10));
                panel.add(new JLabel(new ImageIcon(image)), BorderLayout.CENTER);

                JTextField field = new JTextField(10);
                JPanel input = new JPanel(new FlowLayout(FlowLayout.LEFT));
                input.add(new JLabel("Enter the 6-digit CAPTCHA:"));
                input.add(field);
                panel.add(input, BorderLayout.SOUTH);

                int choice = JOptionPane.showConfirmDialog(
                        null, panel, "GST CAPTCHA", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                if (choice == JOptionPane.OK_OPTION) {
                    answer.set(field.getText().trim());   // leave unset to cancel
                }
            };

            try {
                if (SwingUtilities.isEventDispatchThread()) {
                    ask.run();
                } else {
                    SwingUtilities.invokeAndWait(ask);
                }
            } catch (InterruptedException | InvocationTargetException e) {
                throw new RuntimeException(e);   // reported as an error in the JSON result
            }
        });

        String json = validator.validateGSTIN("27AAAAA0000A1Z2");
        System.out.println(json);
    }
}
```

A JavaFX version of the handler is in [`examples/Test.java`](examples/Test.java).

### Result

`validateGSTIN` always returns a pretty-printed JSON string and does not throw.

**Success**

```json
{
  "success": true,
  "gstin": "27AAAAA0000A1Z2",
  "legalName": "EXAMPLE TRADERS",
  "tradeName": "EXAMPLE TRADERS",
  "effectiveDate": "01/07/2017",
  "constitutionOfBusiness": "Proprietorship",
  "gstinUinStatus": "Active",
  "taxpayerType": "Regular",
  "administrativeOffice": ["..."],
  "otherOffice": ["..."],
  "principalPlaceOfBusiness": "...",
  "natureOfCoreBusinessActivity": "Retail Business",
  "natureOfBusinessActivities": ["Retail Business", "Wholesale Business"],
  "goodsAndServices": {
    "goods":    [ { "hsn": "...", "description": "..." } ],
    "services": [ { "hsn": "...", "description": "..." } ]
  }
}
```

(Values above are illustrative. Fields the portal does not show are returned as empty strings or empty arrays.)

**Failure**

```json
{
  "success": false,
  "gstin": "27AAAAA0000A1Z2",
  "error": "CAPTCHA validation failed or CAPTCHA expired."
}
```

`gstin` is omitted when the input was empty. Errors you can get besides the offline ones above:

| `error` | Cause |
|---------|-------|
| `CAPTCHA handler is not set. Call work(...) before validateGSTIN(...).` | `work(...)` was never called. Returned before any browser starts. |
| `CAPTCHA entry cancelled.` | The handler returned nothing or an empty string. |
| `Invalid CAPTCHA. CAPTCHA must contain exactly 6 digits.` | The handler returned something other than six digits. |
| `CAPTCHA validation failed or CAPTCHA expired.` | The portal rejected the CAPTCHA. |
| `GST portal did not return a search result.` | The portal returned no result within 30 seconds. |
| *(exception message)* | Any other failure, such as no network or a browser start error. |

### Behaviour to be aware of

- **One request, no retries.** Every call is a fresh lookup with a fresh browser and a fresh CAPTCHA. If it fails
  (for example because of a mistyped CAPTCHA), make a new call.
- **Depends on the portal's page.** Results are read from the portal's HTML. If the portal changes its page
  structure, the lookup may fail or return empty fields until the library is updated.
- **Slow by nature.** Each lookup starts a browser, so expect several seconds plus the time the user takes to
  type the CAPTCHA.
- **Threading.** Register the handler before the first call. Concurrent calls are safe as long as your handler is.
- **Offline success is not registration.** `GSTINOfflineValidator` only proves the number is well formed.

## GSTIN layout

```
27  AAAAA0000A  1  Z  2
|   |           |  |  └─ check character (modulo-36 checksum of the first 14)
|   |           |  └──── always the letter Z
|   |           └─────── registration number of this PAN in the state (1-9, then A-Z)
|   └─────────────────── PAN of the taxpayer
└─────────────────────── state / jurisdiction code
```

**Scope:** the standard 15-character GSTIN. Identifiers issued in other layouts (for example UIN or OIDAR
numbers) are rejected as `INVALID_FORMAT`.

**State codes** accepted: `01`-`38`, `97` (Other Territory) and `99` (Centre Jurisdiction). The legacy codes `25`
(Daman and Diu) and `28` (Andhra Pradesh before bifurcation) are accepted so that older registrations still pass.

## Building and testing

```bash
mvn clean verify
```

This compiles for Java 1.8, runs the unit tests, builds the sources and Javadoc jars, and checks that no API
newer than Java 8 is used. The unit tests need no network and no browser.

## Developer

**Hdsoft Solutions**
Email: [hdsoftsolutions@yandex.com](mailto:hdsoftsolutions@yandex.com)
