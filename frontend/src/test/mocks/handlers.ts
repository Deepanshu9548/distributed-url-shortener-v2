import { http, HttpResponse } from 'msw';

export const handlers = [
  http.post('http://localhost:8080/api/auth/login', async ({ request }) => {
    const body = (await request.json()) as any;
    if (body.email === 'test@example.com' && body.password === 'Password123') {
      return HttpResponse.json({
        accessToken: 'mock-access-token',
        refreshToken: 'mock-refresh-token',
        tokenType: 'Bearer',
        expiresIn: 3600,
      });
    }
    return HttpResponse.json({ error: 'Invalid credentials' }, { status: 401 });
  }),

  http.post('http://localhost:8080/api/auth/refresh', async ({ request }) => {
    const body = (await request.json()) as any;
    if (body.refreshToken === 'mock-refresh-token') {
      return HttpResponse.json({
        accessToken: 'new-mock-access-token',
        refreshToken: 'new-mock-refresh-token',
        tokenType: 'Bearer',
        expiresIn: 3600,
      });
    }
    return HttpResponse.json({ error: 'Invalid token' }, { status: 401 });
  }),

  http.get('http://localhost:8080/api/me/links', ({ request }) => {
    const auth = request.headers.get('Authorization');
    if (!auth || !auth.startsWith('Bearer ')) {
      return HttpResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }
    return HttpResponse.json({
      items: [
        { shortCode: 'link1', shortUrl: 'http://localhost:8080/link1', longUrl: 'https://example.com/1', createdAt: '2023-01-01T00:00:00Z', expiresAt: null },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
  }),

  http.post('http://localhost:8080/api/links', async ({ request }) => {
    const auth = request.headers.get('Authorization');
    if (!auth || !auth.startsWith('Bearer ')) {
      return HttpResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }
    const body = (await request.json()) as any;
    if (body.longUrl === 'https://ratelimit.com') {
      return HttpResponse.json({ error: 'Too many requests' }, { status: 429, headers: { 'Retry-After': '5' } });
    }
    return HttpResponse.json({
      shortCode: 'newlink',
      shortUrl: 'http://localhost:8080/newlink',
      longUrl: body.longUrl,
      expiresAt: null,
    }, { status: 201 });
  }),

  http.get('http://localhost:8080/api/links/:code', ({ params, request }) => {
    if (params.code === 'owneronly') {
      const auth = request.headers.get('Authorization');
      if (!auth) return HttpResponse.json({ error: 'Not found' }, { status: 404 });
    }
    if (params.code === 'unknown') {
      return HttpResponse.json({ error: 'Not found' }, { status: 404 });
    }
    return HttpResponse.json({
      shortCode: params.code,
      shortUrl: `http://localhost:8080/${params.code}`,
      longUrl: 'https://example.com',
      createdAt: '2023-01-01T00:00:00Z',
      expiresAt: null,
    });
  }),

  http.get('http://localhost:8080/api/links/:code/stats', ({ params }) => {
    return HttpResponse.json({
      shortCode: params.code,
      clickCount: 10,
      lastClickAt: '2023-01-02T00:00:00Z',
    });
  }),
];
