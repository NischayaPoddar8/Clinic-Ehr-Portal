# Healthcare Appointment & EHR Portal

A Spring Boot and MySQL web application for clinic appointment scheduling, role-based access control, and electronic health records (EHR).

## Features by Role
- **Admin:** Onboard doctors, activate/deactivate user accounts, monitor clinic stats, cancel appointments, view audit logs, and export appointment reports (`.csv` / `.txt`).
- **Doctor:** Configure weekly availability schedules, manage daily appointments, mark consultations completed, and record diagnoses with prescriptions.
- **Patient:** Register an account, search doctors by specialization, book/reschedule/cancel open appointment slots, and view medical records and prescriptions.

## Tech Stack
- **Backend:** Java 21, Spring Boot 3.3.5, Spring Security, Spring Data JPA
- **Database:** MySQL 8
- **Frontend:** HTML5, CSS3, Vanilla JavaScript
- **Build & Test:** Maven, JUnit 5, MockMvc

## Prerequisites
- JDK 21+
- Maven 3.9+
- MySQL 8

## Local Setup & Run

1. **Clone the repository**

       git clone https://github.com/NischayaPoddar8/Clinic-Ehr-Portal.git
       cd Clinic-Ehr-Portal

2. **Create the MySQL databases**

       CREATE DATABASE clinic_ehr;
       CREATE DATABASE clinic_ehr_test;

3. **Set environment variables and start the server** (PowerShell)

       $env:DB_USER="your_mysql_user"
       $env:DB_PASSWORD="your_mysql_password"
       $env:ADMIN_EMAIL="admin@clinic.local"
       $env:ADMIN_PASSWORD="YourAdminPassword"
       $env:ADMIN_NAME="Clinic Admin"
       mvn spring-boot:run

   Open `http://localhost:8080` in your browser and sign in with your configured admin credentials.

## Running Tests

    mvn test -Pmysql-tests