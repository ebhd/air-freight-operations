import type { Airport } from '../domain/Airport';
import { request } from './restUtils';

export async function getAirports(): Promise<Airport[]> {
    const response = await request('/api/airports');
    return response.json();
}
