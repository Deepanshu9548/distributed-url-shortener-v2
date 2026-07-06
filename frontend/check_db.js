const { Client } = require('pg');

const client = new Client({
  connectionString: 'postgres://neondb_owner:npg_cYIb0Hyiv1ed@ep-small-meadow-athyv3g4.c-9.us-east-1.aws.neon.tech/neondb?sslmode=require',
});

async function run() {
  await client.connect();
  console.log('Connected.');
  let res = await client.query('SELECT table_schema, table_name FROM information_schema.tables WHERE table_schema IN (\'shard1\', \'shard2\', \'control\') ORDER BY table_schema, table_name');
  console.log('Tables:', res.rows);
  await client.end();
}

run().catch(console.error);
