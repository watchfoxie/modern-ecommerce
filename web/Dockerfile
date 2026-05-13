FROM node:22.22-alpine AS build

WORKDIR /workspace/web

ARG VITE_API_BASE_URL=/api

ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

COPY web/package.json web/package-lock.json ./

RUN npm ci

WORKDIR /workspace

COPY static ./static
COPY web ./web

WORKDIR /workspace/web

RUN npm run build

FROM node:22.22-alpine AS runtime

WORKDIR /app

RUN addgroup -S web && adduser -S web -G web

COPY web/package.json web/package-lock.json ./
RUN npm ci --omit=dev --ignore-scripts && npm cache clean --force

COPY --chown=web:web --from=build /workspace/web/dist ./dist
COPY --chown=web:web --from=build /workspace/web/server.mjs ./server.mjs
COPY --chown=web:web --from=build /workspace/web/staticAssets.js ./staticAssets.js

USER web

ENV NODE_ENV=production
ENV VITE_PORT=4173
ENV VITE_API_BASE_URL=/api
ENV VITE_API_PROXY_TARGET=http://api-gateway:8080

EXPOSE 4173

CMD ["node", "server.mjs"]
