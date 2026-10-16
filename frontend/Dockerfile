# Two stages: "build" generates the API clients and compiles the Angular app,
# the runtime stage is nginx serving the static files and proxying the APIs.
# docker-compose.yml passes ../contracts in as the "contracts" build context,
# since openapitools.json reads the specs from ../contracts.
FROM node:22-alpine AS build

# openapi-generator-cli is a Java tool: npm's prebuild hook needs a JRE
RUN apk add --no-cache openjdk25-jre-headless

WORKDIR /repo/frontend

# npm ci gets its own layer, before the app code, so code changes reuse it
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY --from=contracts . /repo/contracts
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY security-headers.conf /etc/nginx/security-headers.conf
COPY --from=build /repo/frontend/dist/frontend/browser /usr/share/nginx/html

# The unprivileged image runs as the "nginx" user and listens on 8080
EXPOSE 8080
