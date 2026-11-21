# RestAssured RBAC API Tests

A real, runnable Java test suite for the `rbac-healthcare-system` reference
application, built with JUnit 5 and RestAssured. It is a spec-driven,
end-to-end companion suite: everything in it talks to a real, running
backend over real HTTP -- there is no mocked version of the app anywhere
in this repository.

It covers:

- Authentication and JWT handling (`src/test/java/.../auth`)
- Reading the app's own OpenAPI spec directly out of the running server
  (`src/test/java/.../spec`, `support/OpenApiSpec.java`)
- Full CRUD against appointments and medical records, always verified by
  reading the resource back afterward (`.../crud`)
- JSON Schema / contract validation against real response shapes
  (`.../schema`, `src/test/resources/schemas`)
- The real, verified RBAC permission matrix across all four roles,
  including the row-level filtering that a status-code-only test would
  miss entirely (`.../rbac`)
- Negative and edge-case testing, including a couple of real, reproducible
  gaps in the app's own input handling (`.../negative`)
- API security testing, including a real mass-assignment / privilege-
  escalation finding on self-registration (`.../security`)
- Direct SQLite verification that writes made through the API actually
  persisted (`.../database`)
- Parameterized testing with `@ValueSource`, `@CsvSource`, `@MethodSource`,
  and `@EnumSource` (`.../parameterized`, plus examples throughout)
- Mocking used for exactly one legitimate purpose -- testing our own retry
  helper in isolation with WireMock, never the real backend (`.../mocking`)
- A fast smoke suite (`.../smoke`)
- A capstone suite closing out full coverage of `/api/users` end to end
  (`.../users`)

Parallel test execution is configured in
`src/test/resources/junit-platform.properties`. A GitHub Actions workflow
that stands up the backend from scratch and runs the suite on every pull
request lives at `.github/workflows/api-tests.yml`.

## What this is testing

This suite is written against **rbac-healthcare-system**, an Express +
SQLite + JWT role-based-access-control demo app. You need that app cloned
and running before any of these tests will pass.

## Prerequisites

- Java 17 or newer (JDK)
- Maven 3.8+
- Node.js 18+ (only needed to run the backend you're testing, not this
  project itself)
- Git

## 1. Clone and start the backend

This project assumes `rbac-healthcare-system` is cloned as a **sibling
folder** next to this one, i.e.:

```
some-folder/
├── rbac-healthcare-system/
└── restassured-rbac-api-tests/   <-- this project
```

That layout matters because the database-verification tests read the
backend's SQLite file directly from a relative path
(`../rbac-healthcare-system/backend/database/rbac.db`). If you clone it
somewhere else, pass `-Drbac.dbPath=/full/path/to/rbac.db` when running
the tests (see below), or `-Drbac.baseUri=...` if the backend isn't on
`localhost:5000`.

### Windows (PowerShell), macOS, and Linux -- same commands

```bash
git clone https://github.com/SypherNext/rbac-healthcare-system.git
cd rbac-healthcare-system/backend
npm install
npm run seed     # populates the SQLite database with the seed accounts
npm start        # starts the API on http://localhost:5000
```

Leave that process running in its own terminal window. Confirm it's up:

```bash
curl http://localhost:5000/api-docs
```

(On Windows, `curl` ships with modern PowerShell and Windows 10/11 by
default. If it's missing, the same request works from any browser at
`http://localhost:5000/api-docs`.)

The seed script creates, among others, these accounts every test in this
suite relies on:

| Role | Username | Password |
|---|---|---|
| Administrator | `admin` | `admin123` |
| Doctor | `doctor1` | `pass@123` |
| Nurse | `nurse1` | `pass@123` |
| Patient | `patient1` | `pass@123` |

## 2. Run this test suite

From a second terminal, in this project's own folder:

```bash
mvn test
```

That's the whole command on Windows, macOS, and Linux alike -- Maven
itself is cross-platform, so there's no OS-specific variant needed here.

To run just one class or package while you're working on something
specific:

```bash
mvn test -Dtest=com.example.rbactests.smoke.SmokeTest
mvn test -Dtest=com.example.rbactests.rbac.*
```

To point the suite at a different host/port or a differently located
SQLite file:

```bash
mvn test -Drbac.baseUri=http://localhost:5000 -Drbac.dbPath=../rbac-healthcare-system/backend/database/rbac.db
```

If the SQLite file can't be found at the configured path, the
database-verification tests skip themselves (reported as "skipped", not
"failed") rather than breaking the rest of the run.

## 3. Reports

Maven Surefire writes plain XML/TXT reports to `target/surefire-reports/`
on every run. For a readable HTML report:

```bash
mvn test
mvn surefire-report:report-only
```

Open `target/site/surefire-report.html` in a browser.

## Project layout

```
restassured-rbac-api-tests/
├── pom.xml
├── .github/workflows/api-tests.yml
└── src/test/
    ├── java/com/example/rbactests/
    │   ├── support/            ApiSpec, OpenApiSpec, Database, RetryHelper
    │   ├── spec/               reading/using the real OpenAPI spec
    │   ├── smoke/               fast up/down sanity checks
    │   ├── auth/                login, JWT, the two auth failure modes
    │   ├── crud/                appointments + medical records
    │   ├── schema/              JSON Schema / contract validation
    │   ├── rbac/                the real permission matrix, all 4 roles
    │   ├── negative/            bad input, boundary ids, real gaps found
    │   ├── security/            mass assignment, injection, IDOR checks
    │   ├── database/            direct SQLite read-verification
    │   ├── parameterized/       ValueSource/CsvSource/MethodSource examples
    │   ├── mocking/             WireMock, used only on our own retry helper
    │   └── users/               capstone: full /api/users coverage
    └── resources/
        ├── junit-platform.properties
        └── schemas/             *.json schema files used by schema/ tests
```

## A note on real findings

A few tests in this suite don't just check "did the documented thing
happen" -- they document real, verified behavior of this specific
training app that diverges from what the spec or an intuitive reading of
the role model would suggest, for example:

- `POST /api/appointments` has no role gate at all; any authenticated
  user can create an appointment.
- `GET /api/medical-records` does not filter results for the Nurse role
  the way it does for Doctor and Patient -- a Nurse sees everything.
- Self-registration (`POST /api/auth/register`) accepts a client-supplied
  `role` field with no restriction, so anyone can register themselves as
  an Administrator and get fully privileged access immediately.
- A non-numeric `patient_id` on appointment creation returns a `201`
  with a new id that was never actually persisted.
- Omitting required numeric fields on appointment creation causes an
  unhandled `500` rather than a clean `400`.

These are intentional, documented assertions about the app's actual
current behavior -- not test failures to "fix." They're exactly the kind
of finding a real API test suite is supposed to surface.

## License

Copyright (C) 2026 SypherNext

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0). You may
use, study and change it. If you share a modified version, or run one as a website or service
that other people use, you must make your full modified source code available under the same
license. See [LICENSE](LICENSE) for the full terms.
