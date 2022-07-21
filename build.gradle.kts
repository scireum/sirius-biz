/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

group = "sirius-biz"
version = "DEVELOPMENT-SNAPSHOT"

buildscript {
    repositories {
        mavenLocal()
        maven("https://mvn.scireum.com")
        maven("https://mvn-closed.scireum.com")
    }
    dependencies {
        classpath("com.scireum:sirius-parent:1.0-SNAPSHOT")
    }
}

plugins {
    application
}

apply(plugin = "com.scireum.sirius-parent")

application {
    mainClass.set("sirius.kernel.Setup")
}

dependencies {
    val kernelVersion = "dev-31.3.0"
    val webVersion = "dev-52.5.0"
    val dbVersion = "dev-41.10.1"

    implementation("com.scireum:sirius-kernel") {
        version {
            strictly(kernelVersion)
        }
    }
    implementation("com.scireum:sirius-web") {
        version {
            strictly(webVersion)
        }
    }
    implementation("com.scireum:sirius-db") {
        version {
            strictly(dbVersion)
        }
    }

    testImplementation(group = "com.scireum", name = "sirius-kernel", classifier = "tests") {
        version {
            strictly(kernelVersion)
        }
    }
    testImplementation(group = "com.scireum", name = "sirius-web", classifier = "tests") {
        version {
            strictly(webVersion)
        }
    }
    testImplementation(group = "com.scireum", name = "sirius-db", classifier = "tests") {
        version {
            strictly(dbVersion)
        }
    }

    implementation("org.mariadb.jdbc:mariadb-java-client:2.7.4")

    implementation("net.sf.jt400:jt400:10.7")

    implementation("ru.yandex.clickhouse:clickhouse-jdbc:0.2.6")
    // Required as the version brought by clickhouse-jdbc contains security issues
    implementation("com.fasterxml.jackson.core:jackson-core:2.13.3")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.3")

    implementation("org.apache.ftpserver:ftpserver-core:1.1.2")

    implementation("org.apache.sshd:sshd-core:2.8.0")
    implementation("org.apache.sshd:sshd-sftp:2.8.0")
    implementation("org.apache.sshd:sshd-scp:2.8.0")

    implementation("org.codelibs:jcifs:2.1.31")
    // Required as the version provided by jcifs has security issues
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")

    implementation("com.amazonaws:aws-java-sdk-s3:1.12.261")
    implementation("commons-io:commons-io:2.11.0")
    implementation("commons-net:commons-net:3.8.0")
    implementation("com.rometools:rome:1.18.0")
    implementation("net.sf.sevenzipjbinding:sevenzipjbinding:16.02-2.01")
    implementation("net.sf.sevenzipjbinding:sevenzipjbinding-all-platforms:16.02-2.01")
    implementation("org.yaml:snakeyaml:1.30")
}

