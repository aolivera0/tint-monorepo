import jwksRsa from 'jwks-rsa';
import { env } from '../../config/env.js';

const client = jwksRsa({
  cache: true,
  rateLimit: true,
  jwksRequestsPerMinute: 5,
  jwksUri: 'https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com',
});

export async function getSigningKey(kid: string) {
  return new Promise<string>((resolve, reject) => {
    client.getSigningKey(kid, (err, key) => {
      if (err) return reject(err);
      const signingKey = key?.getPublicKey();
      if (!signingKey) return reject(new Error('No signing key'));
      resolve(signingKey);
    });
  });
}
