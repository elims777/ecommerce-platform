import { Typography, Card, Row, Col, Space, Divider } from 'antd';
import {
    PhoneOutlined,
    MailOutlined,
    EnvironmentOutlined,
    ClockCircleOutlined,
} from '@ant-design/icons';

const { Title, Text, Link } = Typography;

const ContactsPage = () => {
    return (
        <div style={{ maxWidth: 900, margin: '0 auto' }}>
            <Title level={2}>Контакты</Title>

            <Row gutter={[16, 16]}>
                <Col xs={24} md={12}>
                    <Card style={{ height: '100%', borderRadius: 12 }}>
                        <Space direction="vertical" size={20} style={{ width: '100%' }}>
                            <div>
                                <Space align="start">
                                    <PhoneOutlined style={{ fontSize: 20, color: '#1677ff' }} />
                                    <div>
                                        <Text type="secondary">Телефон (бесплатно по России)</Text>
                                        <br />
                                        <Link href="tel:+78002017801" strong style={{ fontSize: 18 }}>
                                            8 (800) 201-78-01
                                        </Link>
                                    </div>
                                </Space>
                            </div>

                            <div>
                                <Space align="start">
                                    <PhoneOutlined style={{ fontSize: 20, color: '#1677ff' }} />
                                    <div>
                                        <Text type="secondary">Городской</Text>
                                        <br />
                                        <Link href="tel:+78212296971" strong style={{ fontSize: 18 }}>
                                            +7 (8212) 29-69-71
                                        </Link>
                                    </div>
                                </Space>
                            </div>

                            <div>
                                <Space align="start">
                                    <MailOutlined style={{ fontSize: 20, color: '#1677ff' }} />
                                    <div>
                                        <Text type="secondary">Email</Text>
                                        <br />
                                        <Link href="mailto:msvkomi@mail.ru" strong style={{ fontSize: 18 }}>
                                            msvkomi@mail.ru
                                        </Link>
                                    </div>
                                </Space>
                            </div>
                        </Space>
                    </Card>
                </Col>

                <Col xs={24} md={12}>
                    <Card style={{ height: '100%', borderRadius: 12 }}>
                        <Space direction="vertical" size={20} style={{ width: '100%' }}>
                            <div>
                                <Space align="start">
                                    <EnvironmentOutlined style={{ fontSize: 20, color: '#1677ff' }} />
                                    <div>
                                        <Text type="secondary">Адрес</Text>
                                        <br />
                                        <Text strong>Россия, г. Москва</Text>
                                    </div>
                                </Space>
                            </div>

                            <div>
                                <Space align="start">
                                    <ClockCircleOutlined style={{ fontSize: 20, color: '#1677ff' }} />
                                    <div>
                                        <Text type="secondary">Режим работы</Text>
                                        <br />
                                        <Text strong>Пн — Пт: 9:00 — 18:00</Text>
                                        <br />
                                        <Text type="secondary">Сб, Вс — выходной</Text>
                                    </div>
                                </Space>
                            </div>
                        </Space>
                    </Card>
                </Col>
            </Row>

            <Divider />

            <Card style={{ borderRadius: 12 }}>
                <Title level={4}>Реквизиты</Title>
                <Row gutter={[24, 12]}>
                    <Col xs={24} sm={8}>
                        <Text type="secondary">Наименование</Text>
                        <br />
                        <Text strong>ООО «МСВ»</Text>
                    </Col>
                    <Col xs={24} sm={8}>
                        <Text type="secondary">ИНН</Text>
                        <br />
                        <Text strong>1101059443</Text>
                    </Col>
                    <Col xs={24} sm={8}>
                        <Text type="secondary">ОГРН</Text>
                        <br />
                        <Text strong>1161101055620</Text>
                    </Col>
                </Row>
            </Card>
        </div>
    );
};

export default ContactsPage;