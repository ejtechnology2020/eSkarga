# Dystrybucja Operaton dla WildFly 41 (podsystem silnika współdzielonego, D9). Obraz tylko dla dev/test (D11).
FROM eclipse-temurin:21-jre
ARG OPERATON_VERSION=2.2.0
ENV OPERATON_HOME=/opt/operaton
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates && rm -rf /var/lib/apt/lists/* \
 && mkdir -p ${OPERATON_HOME} \
 && curl -fsSL "https://github.com/operaton/operaton/releases/download/v${OPERATON_VERSION}/operaton-wildfly-${OPERATON_VERSION}.tar.gz" \
    | tar -xz --strip-components=1 -C ${OPERATON_HOME} \
 && curl -fsSL -o ${OPERATON_HOME}/modules/system/layers/base/org/postgresql/main/postgresql.jar \
    "https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.14/postgresql-42.7.14.jar" || true
COPY module-postgresql.xml ${OPERATON_HOME}/modules/system/layers/base/org/postgresql/main/module.xml
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
EXPOSE 8080 9990
ENTRYPOINT ["/entrypoint.sh"]
