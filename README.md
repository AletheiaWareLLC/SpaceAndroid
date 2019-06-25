![Space](./space.svg)

SpaceAndroid
============

This is an Android implementation of Space - end-to-end encrypted, blockchain-backed, data storage.

Setup
=====
Libraries

    mkdir app/libs
    ln -s <bcandroidaar> bc-android/app-debug.aar
    ln -s <aliasjavalib> app/libs/AliasJava.jar
    ln -s <bcjavalib> app/libs/BCJava.jar
    ln -s <financejavalib> app/libs/FinanceJava.jar
    ln -s <spacejavalib> app/libs/SpaceJava.jar
    ln -s <protobuflitejavalib> app/libs/protobuf-lite-3.0.1.jar

Build
=====

    ./gradlew build
