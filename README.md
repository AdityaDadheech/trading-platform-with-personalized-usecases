# Trading Platform

An AI-assisted swing trading system for Indian equity markets built on Spring Boot + React.

---

## Project Structure

```
trading-platform/
├── trading-common/          Shared DTOs, enums, constants
├── trading-kite-client/     Kite Connect SDK wrapper (auth + WebSocket + REST)
├── trading-ingestion/       Tick consumer → Kafka publisher
├── trading-aggregator/      Candle builder + historical sync + TimescaleDB writer
├── trading-market-api/      Spring Boot app — REST API + WebSocket for the UI
└── trading-ui/              React dashboard (separate build)
```

---

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK | 21 | `java -version` to verify |
| Maven | 3.9+ | or use IntelliJ bundled Maven |
| Docker Desktop | Latest | Runs all infra locally |
| Node.js | 20 LTS | For React frontend |

---

## Step 1: Install the Kite Connect SDK

The Kite Connect Java SDK is not on Maven Central. Install it to your local Maven repo:

1. Download the latest JAR from:
   https://github.com/zerodhatech/javakiteconnect/releases

2. Run (replace filename if version differs):
```bash
mvn install:install-file \
  -Dfile=kiteconnect-3.2.0.jar \
  -DgroupId=com.zerodha \
  -DartifactId=kiteconnect \
  -Dversion=3.2.0 \
  -Dpackaging=jar
```

---

## Step 2: Start Infrastructure

```bash
docker-compose up -d
```

This starts:
- TimescaleDB on port 5432
- Redis on port 6379
- Kafka on port 9092
- Kafka UI on http://localhost:8090

---

## Step 3: Configure Credentials

Copy the dev config template:
```bash
cp trading-kite-client/src/main/resources/application-dev.yml \
   trading-market-api/src/main/resources/application-dev.yml
```

Edit `application-dev.yml` and fill in:
```yaml
kite:
  api-key: YOUR_API_KEY
  api-secret: YOUR_API_SECRET
```

**Never commit application-dev.yml** — it's in `.gitignore`.

---

## Step 4: Build

```bash
mvn clean install -DskipTests
```

---

## Step 5: Run

```bash
cd trading-market-api
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

---

## Step 6: Authenticate with Kite

1. Open in browser: http://localhost:8080/api/v1/auth/login
2. You'll be redirected to Kite's login page
3. Log in with your Zerodha credentials
4. Kite redirects back to your app with the access token
5. WebSocket tick stream starts automatically

After successful auth, copy the access_token from the logs and paste it into
`application-dev.yml` under `kite.access-token` so you don't re-login on restart.

---

## Auth Flow Diagram

```
Browser → GET /api/v1/auth/login
       ← 302 redirect to https://kite.trade/connect/login?api_key=xxx

User logs in at kite.trade

Kite  → GET /api/v1/auth/callback?request_token=yyy&status=success
      ← KiteAuthService.generateSession(request_token)
      ← access_token set on KiteConnect bean
      ← WebSocket connection initiated
```

---

## Key Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/v1/auth/login | Redirects to Kite login page |
| GET | /api/v1/auth/callback | Kite OAuth callback (set as redirect URL in app) |
| GET | /api/v1/auth/status | Check if authenticated |
| GET | /api/v1/instruments | Search instruments |
| GET | /api/v1/ohlcv/{token} | Historical candles |
| GET | /api/v1/quote/{token} | Latest tick from Redis |
| WS  | /ws/ticks | Live tick stream to UI |

---

## .gitignore additions

```
application-dev.yml
application-prod.yml
*.env
```
