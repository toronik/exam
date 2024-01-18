# Exam: Acceptance Test–Driven Development based on [Concordion](https://github.com/concordion/concordion)

[![Stability: Active](https://masterminds.github.io/stability/active.svg)](https://masterminds.github.io/stability/active.html)
![CI](https://github.com/toronik/exam/workflows/CI/badge.svg)
[![](https://jitpack.io/v/toronik/exam.svg)](https://jitpack.io/#toronik/exam)


**Exam** is oriented on **declarative end-to-end black\graybox** application testing in a way a manual tester would do it: send request, verify response\database\message queue etc.

## Features

- Declarative glue-code free approach
- Attractive, flexible documentation
- Widely used set of testing tools under the hood: *dbunit, xml-unit, json-unit*

## Getting started
### 1) Install

```groovy
// Typical microservices setup (Web API + DB + MQ) testing:
testImplementation "io.github.adven27:exam-ms:<version>"

//same as:
//testImplementation "io.github.adven27:exam-ws:<version>"
//testImplementation "io.github.adven27:exam-db:<version>"
//testImplementation "io.github.adven27:exam-mq:<version>"
```
### 2) Use

 [see live spec](https://toronik.github.io/exam/specs/Specs.html) and [demo project](https://github.com/Adven27/service-tests/blob/master/demo/src/test/resources/specs/Specs.md)

## Help
Telegram https://t.me/joinchat/DClprRZ1xDCBtjGexhZOGw
