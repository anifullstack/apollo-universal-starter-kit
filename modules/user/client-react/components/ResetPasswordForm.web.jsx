import React from 'react';
import PropTypes from 'prop-types';
import { withFormik } from 'formik';
import { FieldAdapter as Field } from '@module/core-client-react';
import { translate } from '@module/i18n-client-react';
import { required, minLength, validate, match } from '@module/validation-common-react';
import { Form, RenderField, Button, Alert } from '@module/look-client-react';

import settings from '../../../../settings';

const resetPasswordFormSchema = {
  password: [required, minLength(settings.user.auth.password.minLength)],
  passwordConfirmation: [match('password'), required, minLength(settings.user.auth.password.minLength)]
};

const ResetPasswordForm = ({ values, handleSubmit, error, t }) => {
  return (
    <Form name="resetPassword" onSubmit={handleSubmit}>
      <Field
        name="password"
        component={RenderField}
        type="password"
        label={t('resetPass.form.field.pass')}
        value={values.password}
      />
      <Field
        name="passwordConfirmation"
        component={RenderField}
        type="password"
        label={t('resetPass.form.field.passConf')}
        value={values.passwordConfirmation}
      />
      {error && <Alert color="error">{error}</Alert>}
      <Button color="primary" type="submit">
        {t('resetPass.form.btnSubmit')}
      </Button>
    </Form>
  );
};

ResetPasswordForm.propTypes = {
  handleSubmit: PropTypes.func,
  values: PropTypes.object,
  onSubmit: PropTypes.func,
  submitting: PropTypes.bool,
  error: PropTypes.string,
  t: PropTypes.func
};

const ResetPasswordFormWithFormik = withFormik({
  enableReinitialize: true,
  mapPropsToValues: () => ({ password: '', passwordConfirmation: '' }),
  async handleSubmit(
    values,
    {
      setErrors,
      resetForm,
      props: { onSubmit }
    }
  ) {
    await onSubmit(values)
      .then(() => resetForm())
      .catch(e => setErrors(e));
  },
  validate: values => validate(values, resetPasswordFormSchema),
  displayName: 'LoginForm' // helps with React DevTools
});

export default translate('user')(ResetPasswordFormWithFormik(ResetPasswordForm));
