/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

plugins {
    id("java-library")
    id("com.scireum.sirius-parent") version "11.0.4"
    id("org.sonarqube") version "4.0.0.2929"
}

apply(plugin = "com.scireum.sirius-parent")

dependencies {
    api("com.scireum:sirius-kernel:${property("sirius-kernel")}")
    testImplementation("com.scireum:sirius-kernel:${property("sirius-kernel")}") {
        artifact {
            classifier = "tests"
        }
    }
    api("com.scireum:sirius-web:${property("sirius-web")}")
    testImplementation("com.scireum:sirius-web:${property("sirius-web")}") {
        artifact {
            classifier = "tests"
        }
    }
    api("com.scireum:sirius-db:${property("sirius-db")}")
    testImplementation("com.scireum:sirius-db:${property("sirius-db")}") {
        artifact {
            classifier = "tests"
        }
    }


    implementation("org.mariadb.jdbc:mariadb-java-client:3.0.6")

    implementation("net.sf.jt400:jt400:10.7")

    implementation("com.clickhouse:clickhouse-jdbc:0.3.2-patch11")
    // Required as the version brought by clickhouse-jdbc contains security issues
    implementation("commons-codec:commons-codec:1.13")
    // Required as the version brought by clickhouse-jdbc contains security issues
    implementation("com.fasterxml.jackson.core:jackson-core")
    implementation("com.fasterxml.jackson.core:jackson-databind")

    implementation("org.apache.ftpserver:ftpserver-core:1.2.0")

    implementation("org.apache.sshd:sshd-core:2.9.2")
    implementation("org.apache.sshd:sshd-sftp:2.9.2")
    implementation("org.apache.sshd:sshd-scp:2.9.2")

    implementation("org.codelibs:jcifs:2.1.32")
    // Required as the version provided by jcifs has security issues
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")

    api("com.amazonaws:aws-java-sdk-s3:1.12.272")
    implementation("javax.xml.bind:jaxb-api:2.3.1")
    implementation("commons-io:commons-io:2.11.0")
    implementation("commons-net:commons-net:3.9.0")
    implementation("com.rometools:rome:1.18.0")
    api("net.sf.sevenzipjbinding:sevenzipjbinding:16.02-2.01")
    implementation("net.sf.sevenzipjbinding:sevenzipjbinding-all-platforms:16.02-2.01")
}

sonarqube {
    properties {
        property("sonar.sourceEncoding", "UTF-8")
    }
}
