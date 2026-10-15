# Two stages: "build" compiles the TypeScript, the runtime stage gets only the
# compiled JS and production dependencies. npm ci gets its own layer, ordered
# before the app code, for the same caching reason as Module 12's
# COPY pom.xml first.
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY tsconfig.json tsconfig.build.json ./
COPY src/ src/
RUN npm run build

FROM node:22-alpine
WORKDIR /app

COPY package.json package-lock.json ./
# npm is only needed to install: the container runs node directly. Removing it drops the
# npm CLI's own dependencies, where Trivy found every HIGH CVE in this image (OWASP A06-4).
RUN npm ci --omit=dev \
    && rm -rf /usr/local/lib/node_modules/npm /usr/local/lib/node_modules/corepack \
        /usr/local/bin/npm /usr/local/bin/npx /usr/local/bin/corepack

COPY --from=build /app/dist dist/

# Configuration (DB_*, JWT_SECRET, PORT) comes from the environment;
# docker-compose.yml passes it in, so no secrets are baked into the image.

# Drop root: the node:alpine image ships an unprivileged "node" user.
USER node

EXPOSE 4000
CMD ["node", "dist/main.js"]
