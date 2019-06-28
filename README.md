![Space](./space.svg)

SpaceAndroid
============

This is an Android implementation of Space - end-to-end encrypted, blockchain-backed, digital storage.

Setup
=====

AAR Libraries

    ln -s <bcandroidaardebug> bc-android/app-debug.aar
    ln -s <bcandroidaarrelease> bc-android/app-release.aar

JAR Libraries

    mkdir app/libs
    ln -s <aliasjavalib> app/libs/AliasJava.jar
    ln -s <bcjavalib> app/libs/BCJava.jar
    ln -s <financejavalib> app/libs/FinanceJava.jar
    ln -s <spacejavalib> app/libs/SpaceJava.jar
    ln -s <protobuflitejavalib> app/libs/protobuf-lite-3.0.1.jar

Build
=====

    ./gradlew build
