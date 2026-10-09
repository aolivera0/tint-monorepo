import { initializeApp } from 'firebase/app';
import { getAuth } from 'firebase/auth';

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || 'test',
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || 'test',
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID || 'test',
};

export const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
