import { z } from 'zod';

export const loginSchema = z.object({
  email: z.string().email('Invalid email address').max(320, 'Email too long'),
  password: z.string().min(8, 'Password must be at least 8 characters'),
});

export type LoginFormData = z.infer<typeof loginSchema>;

export const registerSchema = z.object({
  email: z.string().email('Invalid email address').max(320, 'Email too long'),
  password: z.string()
    .min(8, 'Password must be at least 8 characters')
    .max(128, 'Password too long')
    .regex(/[A-Za-z]/, 'Password must contain at least one letter')
    .regex(/[0-9]/, 'Password must contain at least one digit'),
  confirmPassword: z.string(),
}).refine((data) => data.password === data.confirmPassword, {
  message: "Passwords don't match",
  path: ['confirmPassword'],
});

export type RegisterFormData = z.infer<typeof registerSchema>;

const restrictedAliases = ['api', 'auth', 'actuator', 'swagger-ui', 'metrics', 'health', 'admin'];

export const linkSchema = z.object({
  longUrl: z.string()
    .url('Invalid URL format')
    .max(8192, 'URL too long')
    .refine((url) => {
      try {
        const u = new URL(url);
        return u.protocol === 'http:' || u.protocol === 'https:';
      } catch {
        return false;
      }
    }, 'Must be an HTTP(S) URL'),
  customAlias: z.string()
    .regex(/^[0-9a-zA-Z_-]{4,32}$/, 'Alias must be 4-32 characters (alphanumeric, dash, underscore)')
    .refine((alias) => !restrictedAliases.includes(alias.toLowerCase()), 'This alias is reserved')
    .optional()
    .or(z.literal('')),
  ttlType: z.enum(['none', 'ttl', 'expiresAt']),
  ttlSeconds: z.number().positive().optional().or(z.literal('')),
  expiresAt: z.string().optional().or(z.literal('')),
});

export type LinkFormData = z.infer<typeof linkSchema>;
