export const readCookie = (name: string): string => {
    const prefix = `${name}=`
    const cookie = document.cookie
        .split(';')
        .map(part => part.trim())
        .find(part => part.startsWith(prefix))

    return cookie ? cookie.slice(prefix.length) : ''
}

export const getCsrfTokenFromCookie = (): string => {
    return readCookie('XSRF-TOKEN');
}

