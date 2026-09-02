## Leap Program 2026 - TNS Capital

### Team members:  

1. Nathan Carr
2. Sinead King
3. Nokuvimba Chiyaka
4. Tetiana Urbanovych
5. Tiffanie Fitzgerald

### Branching Strategy
- Trunk-based

### Reasons
- Better for small team.
- Frequent commits
- Short lived
- Fewer, smaller possibility of conflicts

### Database

The database runs as a long-lived Postgres container (see `docker-compose.db.yml`),
hosted on a shared machine rather than recreated per build. The `app` container
(`docker-compose.yml`) connects to it over the network instead of starting its own DB.

Connection settings (`DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD`) go in a local
`.env` file (gitignored). These values are **not** freely
customisable per-developer — they must match whatever the shared DB container was
first initialised with, since Postgres only creates the user and runs
`db/schema.sql` once, on first startup with an empty volume. Changing `DB_USER`/
`DB_PASSWORD` locally without also updating them on the DB container itself will
fail with `role "..." does not exist`.
