# largescale

Java·MySQL 대용량 CSV 적재 실험 프로젝트. Java 17, Maven, JDBC를 사용합니다.
초기 구현은 **단일 스레드 배치 INSERT + 배치당 커밋**입니다. 이 구현을 성능 측정의 기준으로 삼고 `CsvLoader`를 구현하여 멀티스레드 배치로 확장합니다.

## 시작

프로젝트 폴더에서 JDK 17로 실행합니다. Maven Wrapper가 Maven 3.9.9를 준비하므로 별도 Maven 설치는 필요하지 않습니다. 첫 빌드에는 네트워크 연결이 필요합니다.

```powershell
.\mvnw.cmd clean verify
java -jar target/largescale-0.1.0-SNAPSHOT.jar validate
```

`validate`는 DB 연결 없이 샘플 CSV의 헤더·타입·레코드를 검증합니다.
MySQL 8.4 이상에서 `sql/schema.sql`을 직접 실행한 뒤, 환경 변수와 연결 설정을 지정합니다.

```powershell
$env:APP_DB_USER = 'your_user'
$env:APP_DB_PASSWORD = 'your_password'
java -jar target/largescale-0.1.0-SNAPSHOT.jar load
```

접속 주소·CSV 경로는 `config/application.properties`에 있습니다. 설정을 복사한 `config/local.properties`는 Git 제외 대상입니다.
`batch.size=1000`이 기본값이며, 양의 정수로 변경할 수 있습니다. 100 / 1,000 / 5,000행 단위로 비교합니다.
각 레코드를 `addBatch()`로 추가하고 설정한 수량마다 `executeBatch()`와 `commit()`을 실행합니다. 마지막 잔여 배치도 커밋합니다.

```powershell
java -jar target/largescale-0.1.0-SNAPSHOT.jar validate config/local.properties
java -jar target/largescale-0.1.0-SNAPSHOT.jar load config/local.properties
```

상대 경로는 실행한 현재 폴더 기준입니다. 사용자·비밀번호는 환경 변수로만 설정하며 커밋하지 않습니다.
실행 명령은 테이블 생성·초기화 SQL을 자동 실행하지 않습니다. 반복 실험 전 전용 테스트 테이블에서 `sql/reset.sql`을 직접 실행하고, 적재 후 `sql/verify.sql`로 결과를 확인합니다.

## 입력 계약과 초기 타입 가정

헤더 순서는 `id,user_id,event,value,payload`입니다. UTF-8, 인용 문자열 내 쉼표·따옴표·줄바꿈을 지원합니다.
실제 구분자가 탭이면 `csv.delimiter=tab`으로 변경합니다. 헤더 없는 파일과 UTF-8 BOM은 현재 지원 범위에서 제외합니다.

| 컬럼 | 초기 MySQL 타입 | 규칙 |
|---|---|---|
| id | BIGINT PRIMARY KEY | CSV에서 지정, 중복은 DB 오류 |
| user_id | BIGINT NOT NULL | 정수 |
| event | VARCHAR(100) NOT NULL | 1~100 문자 |
| value | DECIMAL(20,6) NULL | 반올림 없이 표현 가능한 숫자 |
| payload | TEXT NULL | UTF-8 최대 65,535바이트, 원문 문자열 보존 |

타입은 실제 데이터가 아직 제공되지 않아 둔 **초기 가정**입니다. 실제 value가 문자열이거나 payload가 더 크면 DDL·모델·검증을 함께 변경합니다. payload를 JSON으로 강제하지 않습니다.
`\N`은 value/payload의 NULL 표기이며 빈 payload는 빈 문자열로 보존합니다. 빈 value는 오류입니다.

## 구조

```text
config/             실행 설정
data/sample.csv     인용 문자열·NULL 샘플
sql/                생성·초기화·검증 SQL
src/main/java/com/largescale/
  config/           설정 읽기
  csv/              스트리밍 CSV 파싱·검증
  model/            EventRow
  db/               연결 생성
  loader/           적재 인터페이스·배치 구현·측정 결과
  Main.java         validate/load 명령
src/test/           CSV 계약·배치 트랜잭션 검증
```

## 실패와 측정

첫 오류에서 중단하고 현재 배치의 트랜잭션을 롤백합니다. 앞서 커밋된 배치는 유지되며, 실패 시 확인된 커밋 행 수를 출력합니다.
커밋 도중 연결 손실 시 해당 배치의 커밋 여부가 불명확할 수 있으므로 DB 상태를 확인한 뒤 재실행합니다.
전체 CSV를 리스트로 모으지 않고 레코드별로 읽습니다. validate는 파일 내 ID 중복을 검사하지 않습니다.
적재 시간은 연결 준비를 제외하고 CSV 읽기·타입 검증·배치 INSERT·커밋을 포함합니다. CSV 생성·테이블 초기화·사후 검증은 제외합니다.
초당 처리 행 수는 성공 커밋 행 수 / 적재 시간입니다. 최대 메모리·파일 생성·실험 자동화·멀티스레드 구현은 후속 작업입니다.
초기 실험에서는 드라이버 배치 최적화 옵션을 고정하고, 옵션을 바꾸는 실험은 별도로 기록합니다.

## 의존성 근거

- [Apache Commons CSV](https://github.com/apache/commons-csv): 1.14.1 고정
- [MySQL Connector/J 호환성](https://dev.mysql.com/doc/connector-j/en/connector-j-versions.html): 26.7.0, MySQL 8.4 이상 대상

## 확인한 범위

빌드·패키징 성공, CSV 파싱 6개와 배치 처리 9개 테스트 통과, 실행 JAR에서 샘플 3행 검증을 확인했습니다.
배치 테스트는 JDBC 테스트 더블로 전체/잔여/빈 배치, 파싱·실행·커밋 실패 시 롤백, NULL 바인딩을 검증합니다.
실제 MySQL 연결·INSERT는 접속 환경이 제공되지 않아 아직 검증하지 않았습니다.

## 다음 구현

1. 실제 MySQL에서 단일 스레드 배치의 정확성과 기준 성능 확인
2. 배치 크기 100/1,000/5,000행 비교
3. `MultiThreadBatchLoader`: 리더 1개·제한 큐·연결을 소유하는 작업자 N개
4. 고정 시드 CSV 생성기와 반복 실험 결과 저장

## 작업 계획

- [구현 WBS](docs/wbs.csv): 31개 작업의 선행 작업·완료 기준·시작일·종료일·예상 시간
- [테스트 시나리오](docs/test-scenarios.csv): 15개 기능·성능·오류 테스트

2026-10-12 시작, 1인 개발·평일 8시간·주말 제외 기준입니다. 필수 작업은 10/19까지 44시간 + 여유 4시간, 후속 확장은 10/20~22에 18시간으로 계획했습니다.
단계는 공통 준비 → 단일 스레드 배치 → 멀티스레드 배치 → 통합 실험·복구 확장입니다.
CSV는 최신 WBS 엑셀에서 변환했으며 상태 드롭다운·완료율 수식 같은 엑셀 기능은 포함하지 않습니다.
