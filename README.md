# udacity-test-project
# UdaSecurity

![UdaSecurity Logo](UdaSecurity.png)

The **Udasecurity** company, has created a home security application. This application tracks the status of sensors, monitors camera input, and changes the alarm state of the system based on inputs. Users can arm the system for when they’re home or away as well as disarm the system.

The wild success of your application means that you have to prepare to scale your software. You’ll need to write unit tests for all of the major operations the application performs so that you can safely make more changes in the future. You also need to use maven to streamline the build process and automate your tests and code analysis. More urgently, you need to make sure your application actually does what it’s supposed to do, and so one of writing thorough unit tests will help you find any bugs that already exist.

The image analysis service used by this application has proven popular as well, and another team wants to use it in their project. To accomplish this, you must separate the Image Service from the program and package it as a separate module to be included both in your own project and in other projects.

The end goal for this assignment is to split the project into multiple modules, refactor it to be unit-testable, write unit tests to cover all the main requirements for the Security portion of the application and fix any bugs that you find in the process. You’ll also update the build process to automatically run unit tests, perform static code analysis, and build the code into an executable jar file.
