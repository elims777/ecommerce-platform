import { useEditor, EditorContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Link from '@tiptap/extension-link';
import Image from '@tiptap/extension-image';
import { Button, Space, Tooltip, Modal, Input, message } from 'antd';
import {
    BoldOutlined, ItalicOutlined, UnorderedListOutlined, OrderedListOutlined,
    LinkOutlined, PictureOutlined, UndoOutlined, RedoOutlined,
} from '@ant-design/icons';
import { useState, useRef } from 'react';
import { uploadNewsImage } from '@/api/news';

interface Props {
    value: string;
    onChange: (html: string) => void;
}

/** Редактор текста новости: форматирование, ссылки, картинки из S3. */
const RichTextEditor = ({ value, onChange }: Props) => {
    const [linkModalOpen, setLinkModalOpen] = useState(false);
    const [linkUrl, setLinkUrl] = useState('');
    const [uploading, setUploading] = useState(false);
    const fileInputRef = useRef<HTMLInputElement>(null);

    const editor = useEditor({
        extensions: [
            StarterKit,
            Link.configure({
                openOnClick: false,
                HTMLAttributes: { rel: 'noopener noreferrer', target: '_blank' },
            }),
            Image,
        ],
        content: value,
        onUpdate: ({ editor }) => onChange(editor.getHTML()),
    });

    if (!editor) return null;

    const applyLink = () => {
        const url = linkUrl.trim();
        if (url) {
            editor.chain().focus().extendMarkRange('link').setLink({ href: url }).run();
        } else {
            editor.chain().focus().extendMarkRange('link').unsetLink().run();
        }
        setLinkModalOpen(false);
        setLinkUrl('');
    };

    const openLinkModal = () => {
        setLinkUrl(editor.getAttributes('link').href ?? '');
        setLinkModalOpen(true);
    };

    const handleImageSelected = async (file: File) => {
        setUploading(true);
        try {
            const url = await uploadNewsImage(file);
            editor.chain().focus().setImage({ src: url }).run();
        } catch {
            message.error('Не удалось загрузить картинку');
        } finally {
            setUploading(false);
        }
    };

    const btnType = (active: boolean) => (active ? 'primary' : 'default');

    return (
        <div style={{ border: '1px solid var(--line-2, #d9d9d9)', borderRadius: 8, overflow: 'hidden' }}>
            <div style={{
                display: 'flex', flexWrap: 'wrap', gap: 4, padding: 8,
                borderBottom: '1px solid var(--line-1, #f0f0f0)', background: 'var(--surface, #fafafa)',
            }}>
                <Space.Compact>
                    <Tooltip title="Жирный">
                        <Button size="small" type={btnType(editor.isActive('bold'))} icon={<BoldOutlined />}
                                onClick={() => editor.chain().focus().toggleBold().run()} />
                    </Tooltip>
                    <Tooltip title="Курсив">
                        <Button size="small" type={btnType(editor.isActive('italic'))} icon={<ItalicOutlined />}
                                onClick={() => editor.chain().focus().toggleItalic().run()} />
                    </Tooltip>
                </Space.Compact>

                <Space.Compact>
                    <Tooltip title="Подзаголовок">
                        <Button size="small" type={btnType(editor.isActive('heading', { level: 2 }))}
                                onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}>H2</Button>
                    </Tooltip>
                    <Tooltip title="Подзаголовок меньше">
                        <Button size="small" type={btnType(editor.isActive('heading', { level: 3 }))}
                                onClick={() => editor.chain().focus().toggleHeading({ level: 3 }).run()}>H3</Button>
                    </Tooltip>
                </Space.Compact>

                <Space.Compact>
                    <Tooltip title="Маркированный список">
                        <Button size="small" type={btnType(editor.isActive('bulletList'))} icon={<UnorderedListOutlined />}
                                onClick={() => editor.chain().focus().toggleBulletList().run()} />
                    </Tooltip>
                    <Tooltip title="Нумерованный список">
                        <Button size="small" type={btnType(editor.isActive('orderedList'))} icon={<OrderedListOutlined />}
                                onClick={() => editor.chain().focus().toggleOrderedList().run()} />
                    </Tooltip>
                </Space.Compact>

                <Space.Compact>
                    <Tooltip title="Ссылка">
                        <Button size="small" type={btnType(editor.isActive('link'))} icon={<LinkOutlined />}
                                onClick={openLinkModal} />
                    </Tooltip>
                    <Tooltip title="Картинка">
                        <Button size="small" icon={<PictureOutlined />} loading={uploading}
                                onClick={() => fileInputRef.current?.click()} />
                    </Tooltip>
                </Space.Compact>

                <Space.Compact>
                    <Tooltip title="Отменить">
                        <Button size="small" icon={<UndoOutlined />}
                                onClick={() => editor.chain().focus().undo().run()} />
                    </Tooltip>
                    <Tooltip title="Повторить">
                        <Button size="small" icon={<RedoOutlined />}
                                onClick={() => editor.chain().focus().redo().run()} />
                    </Tooltip>
                </Space.Compact>
            </div>

            <EditorContent editor={editor} className="rf-editor-content" />

            <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                style={{ display: 'none' }}
                onChange={(e) => {
                    const file = e.target.files?.[0];
                    if (file) handleImageSelected(file);
                    e.target.value = '';
                }}
            />

            <Modal
                title="Ссылка"
                open={linkModalOpen}
                onOk={applyLink}
                onCancel={() => setLinkModalOpen(false)}
                okText="Применить"
                cancelText="Отмена"
            >
                <Input
                    value={linkUrl}
                    onChange={(e) => setLinkUrl(e.target.value)}
                    placeholder="https://rfsnab.ru/catalog или /catalog"
                    onPressEnter={applyLink}
                />
                <div style={{ marginTop: 8, fontSize: 12, color: '#888' }}>
                    Пустое поле уберёт ссылку с выделенного текста.
                </div>
            </Modal>
        </div>
    );
};

export default RichTextEditor;
