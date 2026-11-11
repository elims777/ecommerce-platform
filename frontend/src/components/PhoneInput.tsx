import { Input } from 'antd';
import type { InputProps } from 'antd';
import { toPhoneInput } from '../utils/phone';

type PhoneInputProps = Omit<InputProps, 'value' | 'onChange' | 'prefix'> & {
    value?: string;
    onChange?: (value: string) => void;
};

/** Ввод телефона: фиксированный +7, только 10 цифр. Значение формы — строка из 10 цифр. */
const PhoneInput = ({ value, onChange, ...rest }: PhoneInputProps) => (
    <Input
        {...rest}
        prefix="+7"
        inputMode="numeric"
        maxLength={10}
        value={toPhoneInput(value)}
        onChange={(e) => onChange?.(e.target.value.replace(/\D/g, '').slice(0, 10))}
    />
);

export default PhoneInput;
