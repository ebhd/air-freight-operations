import type { DelayReason } from '../domain/DelayReason';
import { request } from './restUtils';

export async function getDelayReasons(): Promise<DelayReason[]> {
    const response = await request('/api/delay-reasons');
    return response.json();
}
