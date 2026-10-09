import express from 'express';
import cors from 'cors';
import router from './routes/v1.js';
import { errorHandler } from './middlewares/error.js';

const app = express();
app.use(cors());
app.use(express.json());
app.use('/api/v1', router);
app.use(errorHandler);

export default app;
