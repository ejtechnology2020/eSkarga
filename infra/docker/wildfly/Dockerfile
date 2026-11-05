# Dystrybucja Operaton dla WildFly (podsystem silnika współdzielonego, D9). Obraz tylko dla dev/test (D11).
# Archiwum operaton-bpm-wildfly-<wersja>.tar.gz (zasoby wydania GitHub) zawiera server/wildfly-<wersja WildFly>/ – ten katalog
# staje się OPERATON_HOME. Suma SHA-256 z checksums_sha256.txt wydania; przy zmianie wersji podaj obie wartości (compose/.env).
# JDK, nie JRE: moduł org.mybatis.mybatis dystrybucji wymaga jdk.attach i jdk.jdi (ModuleLoadError: jdk.attach przy wdrażaniu WAR-a)
FROM eclipse-temurin:21-jdk
ARG OPERATON_VERSION=2.2.0
ARG OPERATON_SHA256=6bc09729936ccf735c6352fe2d0f89cf3ca89d4d1aee9ad973d4d909d4297a0e
ARG OPERATON_DIST_URL=https://github.com/operaton/operaton/releases/download/v${OPERATON_VERSION}/operaton-bpm-wildfly-${OPERATON_VERSION}.tar.gz
ARG POSTGRESQL_JDBC_VERSION=42.7.14
ENV OPERATON_HOME=/opt/operaton
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates && rm -rf /var/lib/apt/lists/* \
 && curl -fsSL -o /tmp/operaton.tar.gz "${OPERATON_DIST_URL}" \
 && echo "${OPERATON_SHA256}  /tmp/operaton.tar.gz" | sha256sum -c - \
 && mkdir -p /tmp/operaton && tar -xzf /tmp/operaton.tar.gz -C /tmp/operaton \
 && mv /tmp/operaton/server/wildfly-* ${OPERATON_HOME} \
 && rm -rf /tmp/operaton /tmp/operaton.tar.gz \
 # przykładowa aplikacja procesowa (użytkownicy demo, dane testowe) nie ma czego szukać w środowisku eSkargi
 && rm -f ${OPERATON_HOME}/standalone/deployments/operaton-example-invoice-*.war \
 # sterownik PostgreSQL jako moduł WildFly (eskarga-datasource.cli: jdbc-driver=postgresql, moduł org.postgresql)
 && mkdir -p ${OPERATON_HOME}/modules/system/layers/base/org/postgresql/main \
 && curl -fsSL -o ${OPERATON_HOME}/modules/system/layers/base/org/postgresql/main/postgresql.jar \
    "https://repo1.maven.org/maven2/org/postgresql/postgresql/${POSTGRESQL_JDBC_VERSION}/postgresql-${POSTGRESQL_JDBC_VERSION}.jar"
COPY module-postgresql.xml ${OPERATON_HOME}/modules/system/layers/base/org/postgresql/main/module.xml
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
EXPOSE 8080 9990
ENTRYPOINT ["/entrypoint.sh"]
