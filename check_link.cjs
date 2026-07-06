const { Client } = require('pg');
const client = new Client({ connectionString: 'postgresql://neondb_owner:npg_cYIb0Hyiv1ed@ep-small-meadow-athyv3g4.c-9.us-east-1.aws.neon.tech/neondb?sslmode=require' });
async function run() {
  await client.connect();
  let res = await client.query("SELECT * FROM shard1.links WHERE short_code = 'oy6yYF6Rwc'");
  if (res.rows.length > 0) console.log("shard1:", res.rows[0]);
  res = await client.query("SELECT * FROM shard2.links WHERE short_code = 'oy6yYF6Rwc'");
  if (res.rows.length > 0) console.log("shard2:", res.rows[0]);
  await client.end();
}
run().catch(console.error);
