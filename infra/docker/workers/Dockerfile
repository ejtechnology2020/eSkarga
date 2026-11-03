FROM eclipse-temurin:21-jre
WORKDIR /opt/eskarga
COPY workers/target/eskarga-workers.jar ./eskarga-workers.jar
COPY workers/target/lib ./lib
ENTRYPOINT ["java", "-cp", "eskarga-workers.jar:lib/*", "pl.ejtechnology.tk.eSkarga.workers.WorkersMain"]
