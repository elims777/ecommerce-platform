import { Typography, Card, Row, Col, Space } from 'antd';
import { company } from '@/config/company';
import {
    CheckCircleOutlined,
    TruckOutlined,
    TagOutlined,
    TeamOutlined,
    FilePdfOutlined,
} from '@ant-design/icons';

const { Title, Paragraph, Text } = Typography;

const advantages = [
    {
        icon: <CheckCircleOutlined style={{ fontSize: 32, color: '#1677ff' }} />,
        title: 'Широкий ассортимент',
        description: 'Более 10 000 наименований промышленного оборудования и расходных материалов',
    },
    {
        icon: <TagOutlined style={{ fontSize: 32, color: '#52c41a' }} />,
        title: 'Приемлемые цены',
        description: 'Прямые контракты с производителями позволяют предлагать лучшие цены на рынке',
    },
    {
        icon: <TruckOutlined style={{ fontSize: 32, color: '#faad14' }} />,
        title: 'Быстрая доставка',
        description: 'Самые короткие сроки поставки по всей России',
    },
    {
        icon: <TeamOutlined style={{ fontSize: 32, color: '#722ed1' }} />,
        title: 'Гибкие условия',
        description: 'Система скидок для постоянных клиентов, индивидуальный подход к каждому заказу',
    },
];

const AboutPage = () => {
    return (
        <div style={{ maxWidth: 900, margin: '0 auto' }}>
            <Title level={2}>О компании</Title>

            <Card style={{ marginBottom: 24 }}>
                <Paragraph style={{ fontSize: 16 }}>
                    <Text strong>{company.legalName}</Text> — надёжный партнёр в сфере комплексного
                    снабжения предприятий. Мы специализируемся на поставках средств
                    индивидуальной защиты, противопожарного оборудования, медицинских
                    товаров, спецодежды и промышленной химии.
                </Paragraph>
                <Paragraph style={{ fontSize: 16 }}>
                    Компания работает с {company.founded} года, обеспечивая предприятия и организации
                    всем необходимым для безопасной и эффективной работы. Среди наших
                    клиентов — промышленные предприятия, медицинские учреждения,
                    государственные организации.
                </Paragraph>
                <Paragraph style={{ fontSize: 16 }}>
                    Мы работаем по 44-ФЗ и 223-ФЗ, участвуем в государственных
                    закупках и тендерах.
                </Paragraph>
            </Card>

            <Title level={3} style={{ marginBottom: 16 }}>
                Почему выбирают нас
            </Title>

            <Row gutter={[16, 16]}>
                {advantages.map((item) => (
                    <Col xs={24} sm={12} key={item.title}>
                        <Card hoverable style={{ height: '100%', borderRadius: 12 }}>
                            <Space direction="vertical" align="center" style={{ width: '100%', textAlign: 'center' }}>
                                {item.icon}
                                <Title level={5} style={{ marginTop: 12, marginBottom: 4 }}>
                                    {item.title}
                                </Title>
                                <Text type="secondary">{item.description}</Text>
                            </Space>
                        </Card>
                    </Col>
                ))}
            </Row>

            <Card style={{ marginTop: 24 }}>
                <Row gutter={24}>
                    <Col span={12}>
                        <Text type="secondary">ИНН</Text>
                        <br />
                        <Text strong>{company.inn}</Text>
                    </Col>
                    <Col span={12}>
                        <Text type="secondary">ОГРН</Text>
                        <br />
                        <Text strong>{company.ogrn}</Text>
                    </Col>
                </Row>
            </Card>

            <Title level={3} style={{ marginTop: 24, marginBottom: 16 }}>
                Специальная оценка условий труда (СОУТ)
            </Title>
            <Card>
                <Paragraph style={{ fontSize: 16 }}>
                    В соответствии с требованиями Федерального закона от 28.12.2013
                    № 426-ФЗ «О специальной оценке условий труда» в ООО «МСВ» проведена
                    специальная оценка условий труда (СОУТ) на рабочих местах.
                </Paragraph>
                <Space direction="vertical">
                    <a href="/sout-svodnaya-vedomost-2026.pdf" target="_blank" rel="noopener">
                        <FilePdfOutlined /> Сводная ведомость результатов проведения специальной оценки условий труда, 2026 г.
                    </a>
                    <a href="/sout-perechen-meropriyatiy-2026.pdf" target="_blank" rel="noopener">
                        <FilePdfOutlined /> Перечень рекомендуемых мероприятий по улучшению условий труда, 2026 г.
                    </a>
                </Space>
            </Card>
        </div>
    );
};

export default AboutPage;