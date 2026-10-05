# SonarQube Quality Gate

Every Jenkins build runs a SonarQube analysis after the tests. If the code fails the
**TNS Capital Gate**, the build fails — so poor-quality code can't be merged.

Each developer runs their own SonarQube and Jenkins. This is a one-time setup.

## How it works

1. The `Test` stage runs the unit tests. JaCoCo writes a coverage report to
   `target/site/jacoco/jacoco.xml`.
2. The `SonarQube Analysis` stage runs `mvn sonar:sonar` against the server Jenkins
   knows as `sonarserver`, using the `sonar-token` credential.
3. `-Dsonar.qualitygate.wait=true` makes Maven wait for the gate result and exit
   non-zero if it fails, which fails the stage and the build.

The plugin versions and project key (`tns-capital`) are in `pom.xml`. The stage is in
`Jenkinsfile`.

## The quality gate

The gate checks **new code** only — code added or changed since the previous version.
Existing issues don't fail the build, but new ones do.

| Condition (on New Code)          | Fails when      | Why                                          |
|----------------------------------|-----------------|----------------------------------------------|
| Issues                           | greater than 0  | No new issues of any kind                    |
| Blocker Severity Issues          | greater than 0  | No new blocker issues                        |
| Security Rating                  | worse than A    | No new vulnerabilities, including critical   |
| Reliability Rating               | worse than A    | No new bugs                                  |
| Security Hotspots Reviewed       | less than 100%  | Every new hotspot is reviewed before merge   |
| Coverage                         | less than 80%   | New code is tested                           |
| Duplicated Lines (%)             | greater than 3% | No copy-pasted code                          |

## Setup

### 1. Start SonarQube

Skip this if you already have SonarQube running from the Sprint 7 demo (`sonarqube-sprint7`).

```bash
docker run -d --name sonarqube-sprint7 --restart unless-stopped -p 8082:9000 sonarqube:community
```

It takes 1–2 minutes to start. It's ready when this returns `"status":"UP"` (it returns
nothing or `STARTING` until then — wait and run it again):

```bash
curl -s http://YOUR-IP-ADDRESS:8082/api/system/status
```

Open `http://<host>:8082` and log in as `admin` / `admin`. You'll be asked to change the
password.

`--restart unless-stopped` brings it back after a reboot. If you set it up during the demo
without that flag, add it: `docker update --restart unless-stopped sonarqube-sprint7`.

If it exits straight away, check `docker logs sonarqube-sprint7`. A `vm.max_map_count` error
means the host needs `sudo sysctl -w vm.max_map_count=262144`. If the analysis fails with
"indexing failures", the disk is over 90% full — free some space.

### 2. Create the quality gate

1. **Quality Gates → Create**, name it `TNS Capital Gate`.
2. Add each condition from the table above, on **New Code**.

The project itself is created automatically by the first analysis (step 5). After that:
**TNS Capital → Policies → Quality Gate → Always use a specific Quality Gate →
TNS Capital Gate → Save**.

### 3. Generate a token

**My Account → Security → Generate Token**, type *Global Analysis Token*. Copy it — it's
only shown once. Never commit it, or paste it into chats, tickets or pull requests.

### 4. Configure Jenkins

The `Jenkinsfile` expects these names exactly:

| Where (Manage Jenkins →)       | Name           | Value                                         |
|--------------------------------|----------------|-----------------------------------------------|
| System → SonarQube servers     | `sonarserver`  | Server URL `http://<host>:8082`               |
| Credentials → Secret text      | `sonar-token`  | The token from step 3                         |
| Tools → Maven installations    | `maven-3.8.4`  | Install automatically, 3.8.4                  |
| Tools → JDK installations      | `JDK21`        | A JDK 21 install                              |

The **SonarQube Scanner for Jenkins** plugin must be installed for `sonarserver` to appear.

If any of these are missing, the build fails before or at the `SonarQube Analysis` stage.

### 5. Run the analysis locally

From the project root, replacing `<host>` and `<your-token>`:

```bash
mvn -B clean test sonar:sonar \
  -Dsonar.host.url=http://<host>:8082 \
  -Dsonar.token=<your-token> \
  -Dsonar.qualitygate.wait=true > ~/sonar-run.log 2>&1; tail -5 ~/sonar-run.log
grep 'QUALITY GATE' ~/sonar-run.log
```

It takes a few minutes because it runs the full test suite first. The output is saved to
`~/sonar-run.log` because it's too long to read in the terminal.

- `BUILD SUCCESS` and `QUALITY GATE STATUS: PASSED` — the code meets the gate.
- `BUILD FAILURE` and `QUALITY GATE STATUS: FAILED` — follow the link to see why
  (see below).
- `BUILD FAILURE` with no `QUALITY GATE` line — the build failed before the analysis
  (compile error or failing test). Check `grep ERROR ~/sonar-run.log`.

The token is saved in your shell history; clear it with `history -c` if you share the machine.

## When the gate fails

1. Open the `SonarQube Analysis` stage log in Jenkins and follow the dashboard link.
2. On the project page, the **New Code** tab lists exactly which conditions failed and
   which issues caused them.
3. Fix the issues, push again, and the build reruns the gate.

A finding that is genuinely a false positive can be marked **Accept** or
**False Positive** on the issue in SonarQube, with a comment explaining why.

## Limitations

- **Pull requests:** Community Edition can't analyse pull requests or comment on them.
  The gate result appears in the Jenkins build for the branch, not on the GitHub PR.
- **Embedded database:** fine for a single developer. A shared team server should run
  SonarQube with PostgreSQL.
- **If SonarQube is down,** the `SonarQube Analysis` stage fails the build. Start it with
  step 1.
