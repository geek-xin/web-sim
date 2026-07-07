import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchJson } from './api';

describe('fetchJson', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('unwraps Result success data', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: vi.fn().mockResolvedValue({ success: true, data: { id: 'sim-1' } }),
    });
    vi.stubGlobal('fetch', fetchMock);

    await expect(fetchJson<{ id: string }>('/admin/api/simulations')).resolves.toEqual({ id: 'sim-1' });
    expect(fetchMock).toHaveBeenCalledWith('/admin/api/simulations', {});
  });

  it('throws when Result success is false', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: vi.fn().mockResolvedValue({ success: false, code: 'BAD_REQUEST', message: 'Invalid simulation' }),
      }),
    );

    await expect(fetchJson('/admin/api/simulations')).rejects.toThrow('Invalid simulation');
  });
});
