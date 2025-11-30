

# Use OpenJDK 17 as base image
FROM eclipse-temurin:17-jdk


# Set working directory in container
WORKDIR /app

# Copy your jar file into the container
COPY target/othello-0.0.1-SNAPSHOT.jar othello.jar

# Expose the port your app uses (adjust if needed)
EXPOSE 8081

# Command to run your app
ENTRYPOINT ["java", "-jar", "othello.jar"]

