# syntax=docker/dockerfile:1

# Backend build image
FROM maven:3.9.16-eclipse-temurin-21-alpine AS backend-build
WORKDIR /workspace/backend

COPY backend/pom.xml ./pom.xml
RUN mvn -B -ntp dependency:go-offline

COPY backend/src ./src
RUN mvn -B -ntp clean package -DskipTests

# Backend runtime image
FROM eclipse-temurin:21-jre-alpine AS backend
WORKDIR /app

RUN addgroup -S ptmoc && adduser -S -G ptmoc ptmoc
COPY --from=backend-build --chown=ptmoc:ptmoc \
    /workspace/backend/target/ptmoc-backend-1.0.0.jar \
    /app/ptmoc-backend.jar

USER ptmoc
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=10 \
    CMD wget -qO- http://127.0.0.1:8080/api/ptmoc/health >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/ptmoc-backend.jar"]

# Frontend build image
FROM node:24-alpine AS frontend-build
WORKDIR /workspace/frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY frontend/ ./
RUN npm run build

# Frontend runtime image and reverse proxy
FROM nginx:1.30.4-alpine AS frontend

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=frontend-build /workspace/frontend/dist/ /usr/share/nginx/html/

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=5 \
    CMD wget -qO- http://127.0.0.1/ >/dev/null || exit 1

CMD ["nginx", "-g", "daemon off;"]
