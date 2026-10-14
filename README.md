# largescale

Java 17 · Gradle · MySQL 기반 대용량 CSV 적재 실험 프로젝트.
현재 단일 스레드 JDBC 배치 적재를 지원하며, 멀티스레드 배치로 확장할 예정입니다.

## 빌드와 CSV 검증

JDK 17과 MySQL 8.4 이상을 사용합니다. 프로젝트 폴더에서 실행하며, Gradle은 Wrapper로 준비합니다.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\gradlew.bat clean build
.\gradlew.bat run --args="validate"
```

`validate`는 DB 연결 없이 CSV 형식을 검증합니다.

## DB 적재

1. MySQL에서 `sql/schema.sql`을 실행합니다.
2. `config/application.yaml`의 CSV 경로, 구분자(`comma` / `tab`), DB URL, 배치 크기를 설정합니다. 기본 배치 크기는 1,000행입니다.
3. 접속 계정을 환경 변수로 지정하고 실행합니다.

```powershell
$env:APP_DB_USER = 'your_user'
$env:APP_DB_PASSWORD = 'your_password'
.\gradlew.bat run --args="load"
```

개인 설정은 Git에서 제외되는 `config/local.yaml`에 저장하고 `run --args="load config/local.yaml"`로 지정할 수 있습니다. 상대 경로는 현재 실행 폴더 기준입니다.

배치마다 커밋하며 마지막 잔여 배치도 처리합니다. 오류 시 현재 배치를 롤백하고 중단하며, 앞서 커밋된 데이터는 유지됩니다. 커밋 도중 연결이 끊기면 DB 상태를 확인한 뒤 재실행합니다.
결과 확인은 `sql/verify.sql`, 테스트 테이블 초기화는 `sql/reset.sql`을 사용합니다.

## CSV 형식

UTF-8이며 아래 순서의 헤더가 필요합니다.

```csv
id,user_id,event,value,payload
1,user-0000,page_view,123.45,abcdefghij
```

- `id`: 정수 기본 키, `user_id`와 `event`: 최대 100자 문자열
- `value`: `DECIMAL(20,6)`, `payload`: 일반 문자열(`TEXT`)
- `value`와 `payload`의 NULL은 `\N`으로 표기합니다. 빈 payload는 빈 문자열로 보존합니다.
- 쉼표·따옴표·줄바꿈이 포함된 필드는 CSV 인용 규칙을 따릅니다.

기존 `user_id BIGINT` 테이블은 `sql/migrate-user-id.sql`로 타입을 변경할 수 있습니다. `user-0000` 같은 원본 식별자는 CSV에서 다시 적재합니다.

## 배포와 문서

`./gradlew installDist`로 실행 스크립트를 포함한 배포 폴더를 생성하고, `distZip`으로 배포 ZIP을 만듭니다. Windows에서는 `./gradlew` 대신 `.\gradlew.bat`를 사용합니다.

- 배포 폴더: `build/install/largescale` — 이 폴더에서 `bin/largescale.bat validate` 실행
- 배포 ZIP: `build/distributions/largescale-0.1.0-SNAPSHOT.zip`
- [구현 WBS](docs/wbs.csv) · [테스트 시나리오](docs/test-scenarios.csv)

단위 테스트는 빌드에 포함됩니다. 실제 MySQL 적재 검증과 성능 측정은 별도로 진행합니다.
