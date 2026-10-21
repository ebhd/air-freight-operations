// one booked uld as sent by UldBookingResponse in the cargo plan
export type UldBookingResponse = {
    uldCode: string;
    weight: WeightValue;
    destination: string;
    position: string | null;
};

type WeightValue = {
    value: number;
    unit: string;
};
