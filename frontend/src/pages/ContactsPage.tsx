import { Typography, Card, Row, Col, Space, Divider } from 'antd';
import { company } from '@/config/company';
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
                                        <Link href={`tel:${company.phone.free.replace(/[^+\d]/g, '')}`} strong style={{ fontSize: 18 }}>
                                            {company.phone.free}
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
                                        <Link href={`tel:${company.phone.city.replace(/[^+\d]/g, '')}`} strong style={{ fontSize: 18 }}>
                                            {company.phone.city}
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
                                        <Link href={`mailto:${company.email.sales}`} strong style={{ fontSize: 18 }}>
                                            {company.email.sales}
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
                                        <Text strong>{company.address.full}</Text>
                                    </div>
                                </Space>
                            </div>

                            <div>
                                <Space align="start">
                                    <ClockCircleOutlined style={{ fontSize: 20, color: '#1677ff' }} />
                                    <div>
                                        <Text type="secondary">Режим работы</Text>
                                        <br />
                                        <Text strong>{company.workHours}</Text>
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
                        <Text strong>{company.legalName}</Text>
                    </Col>
                    <Col xs={24} sm={8}>
                        <Text type="secondary">ИНН</Text>
                        <br />
                        <Text strong>{company.inn}</Text>
                    </Col>
                    <Col xs={24} sm={8}>
                        <Text type="secondary">ОГРН</Text>
                        <br />
                        <Text strong>{company.ogrn}</Text>
                    </Col>
                </Row>
            </Card>
        </div>
    );
};

export default ContactsPage;