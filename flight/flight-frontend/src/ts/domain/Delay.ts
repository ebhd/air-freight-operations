export type DelayType = 'DEPARTURE' | 'ARRIVAL';

export type AddDelayRequest = {
    type: DelayType;
    minutes: number;
    reasonCode: string;
    description: string;
};

export type UpdateDelayRequest = Pick<AddDelayRequest, 'minutes' | 'description'>;

export type Delay = AddDelayRequest & { id: string };
