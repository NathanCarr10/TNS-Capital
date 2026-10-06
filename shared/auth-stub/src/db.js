const { Pool } = require('pg');
require('dotenv').config();

const pool = new Pool({
  // In Docker: use 'postgres' (service name). Locally: use localhost or env var
  host: process.env.DB_HOST || 'postgres',
  port: process.env.DB_PORT || 5432,
  database: process.env.DB_NAME || 'tns_capital',
  user: process.env.DB_USER || 'tns-capital-db-user',
  password: process.env.DB_PASSWORD,
});

pool.on('error', (err) => {
  console.error('Unexpected error on idle client', err);
  process.exit(-1);
});

module.exports = pool;
