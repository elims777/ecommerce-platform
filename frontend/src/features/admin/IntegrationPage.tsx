import { Card, Typography, Descriptions, Tag, Button, Alert } from 'antd';
import { SyncOutlined, LinkOutlined, CheckCircleOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';

const { Title, Text, Paragraph } = Typography;

const IntegrationPage = () => {
    const navigate = useNavigate();

    return (
        <div>
            <Title level={2} style={{ marginBottom: 24 }}>
                Интеграция 1С
            </Title>

            <Alert
                title="CommerceML 2.10"
                description="Обмен данными с 1С настроен через протокол CommerceML 2.10. Товары из 1С попадают в категорию «Импорт из 1С» и требуют ручного распределения по категориям и активации."
                type="info"
                showIcon
                style={{ marginBottom: 24 }}
            />

            <Card
                title={
                    <span>
            <SyncOutlined /> Настройки обмена
          </span>
                }
                style={{ marginBottom: 24, borderRadius: 12 }}
            >
                <Descriptions column={1} size="small">
                    <Descriptions.Item label="URL обмена">
                        <Text copyable code>
                            http://ВАШ_ДОМЕН:8085/1c-exchange
                        </Text>
                    </Descriptions.Item>
                    <Descriptions.Item label="Протокол">
                        CommerceML 2.08
                    </Descriptions.Item>
                    <Descriptions.Item label="Авторизация">
                        Basic Auth (логин/пароль из .env)
                    </Descriptions.Item>
                    <Descriptions.Item label="Статус">
                        <Tag icon={<CheckCircleOutlined />} color="success">
                            Активен
                        </Tag>
                    </Descriptions.Item>
                </Descriptions>
            </Card>

            <Card
                title="Импортированные товары"
                style={{ marginBottom: 24, borderRadius: 12 }}
            >
                <Paragraph>
                    Товары, загруженные из 1С, попадают в категорию <Text strong>«Импорт из 1С»</Text> в
                    неактивном состоянии. Для публикации на сайте необходимо:
                </Paragraph>
                <Paragraph>
                    1. Перейти в каталог и выбрать категорию «Импорт из 1С»
                </Paragraph>
                <Paragraph>
                    2. Выделить нужные товары чекбоксами
                </Paragraph>
                <Paragraph>
                    3. Нажать «Переместить в категорию» и выбрать целевую категорию
                </Paragraph>
                <Paragraph>
                    4. Активировать товары переключателем в колонке «Акт.»
                </Paragraph>

                <Button
                    type="primary"
                    icon={<LinkOutlined />}
                    size="large"
                    onClick={() => navigate('/admin/products')}
                    style={{ marginTop: 8 }}
                >
                    Перейти в каталог
                </Button>
            </Card>

            <Card
                title="Настройка в 1С"
                style={{ borderRadius: 12 }}
            >
                <Paragraph>
                    Для настройки обмена в 1С (УНФ / УТ / Бухгалтерия):
                </Paragraph>
                <Paragraph>
                    1. Перейдите в <Text strong>Администрирование → Обмен данными → Узлы обмена</Text>
                </Paragraph>
                <Paragraph>
                    2. Создайте новый узел обмена с сайтом
                </Paragraph>
                <Paragraph>
                    3. Укажите URL обмена, логин и пароль
                </Paragraph>
                <Paragraph>
                    4. Настройте расписание автоматического обмена (рекомендуется каждые 30 минут)
                </Paragraph>
            </Card>
        </div>
    );
};

export default IntegrationPage;