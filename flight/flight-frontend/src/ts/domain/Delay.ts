export type AddDelayRequest = {
    minutes: number;
    reasonCode: string;
    description: string;
};

export type UpdateDelayRequest = {
    minutes: number;
    description: string;
};

export type Delay = AddDelayRequest & { id: string };
