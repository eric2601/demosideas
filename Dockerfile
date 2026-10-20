# Imagen única: API + panel web (compilado). No incluye datos de demostración.
FROM node:22-alpine AS web
WORKDIR /app/web
COPY web/package*.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

FROM node:22-alpine AS server
WORKDIR /app/server
COPY server/package*.json ./
RUN npm ci
COPY server/ ./
RUN npm run build && npm prune --omit=dev

FROM node:22-alpine
RUN apk add --no-cache postgresql16-client
ENV NODE_ENV=production
WORKDIR /app/server
COPY --from=server /app/server/node_modules ./node_modules
COPY --from=server /app/server/dist ./dist
COPY --from=server /app/server/migrations ./migrations
COPY --from=server /app/server/package.json ./package.json
COPY --from=web /app/web/dist /app/web/dist
ENV WEB_DIST_DIR=/app/web/dist
EXPOSE 3000
USER node
CMD ["node", "dist/index.js"]
