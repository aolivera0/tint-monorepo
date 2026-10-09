import 'dotenv/config';
import app from './app.js';
import { env } from './config/env.js';

const port = parseInt(env.IDENTITY_API_PORT, 10);
app.listen(port, () => {
  console.log(`identity-api listening on port ${port}`);
});
