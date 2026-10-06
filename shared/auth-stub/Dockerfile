# A single-stage build - unlike the mission service, there's no separate
# "compile" step for Node. npm install still gets its own layer, ordered
# before the app code, for the same caching reason as Module 12's
# COPY pom.xml first.
FROM node:20-alpine
WORKDIR /app

COPY package.json .
RUN npm install --omit=dev

COPY server.js .
COPY src/ src/

# Accept build args and create .env file
ARG DB_HOST
ARG DB_PORT
ARG DB_NAME
ARG DB_USER
ARG DB_PASSWORD
ARG JWT_SECRET

RUN printf "DB_HOST=%s\nDB_PORT=%s\nDB_NAME=%s\nDB_USER=%s\nDB_PASSWORD=%s\nJWT_SECRET=%s\n" \
  "${DB_HOST:-postgres}" "${DB_PORT:-5432}" "${DB_NAME:-tns_capital}" \
  "${DB_USER}" "${DB_PASSWORD}" "${JWT_SECRET}" > .env && chmod 644 .env

# Drop root: the node:alpine image ships an unprivileged "node" user.
USER node

EXPOSE 4000
CMD ["node", "server.js"]
