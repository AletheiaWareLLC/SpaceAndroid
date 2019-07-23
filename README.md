![Space](./space.svg)

SpaceAndroid
============

This is an Android implementation of Space - secure, private, storage.

Setup
=====

AAR Libraries

    ln -s <awcommonandroidaardebug> aletheiaware-common-android/app-debug.aar
    ln -s <awcommonandroidaarrelease> aletheiaware-common-android/app-release.aar

    ln -s <bcandroidaardebug> bc-android/app-debug.aar
    ln -s <bcandroidaarrelease> bc-android/app-release.aar

JAR Libraries

    mkdir app/libs
    ln -s <awcommonjavalib> app/libs/AletheiaWareCommonJava.jar
    ln -s <aliasjavalib> app/libs/AliasJava.jar
    ln -s <bcjavalib> app/libs/BCJava.jar
    ln -s <financejavalib> app/libs/FinanceJava.jar
    ln -s <spacejavalib> app/libs/SpaceJava.jar
    ln -s <protobuflitejavalib> app/libs/protobuf-lite-3.0.1.jar

JAR Test Libraries

    ln -s <bcjavatestlib> app/libs/BCJavaTest.jar

Build
=====

    ./gradlew build
