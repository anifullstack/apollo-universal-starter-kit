import { knex } from '@module/database-server-ts';

export default class $Module$ {
  public $module$s() {
    return knex.select();
  }
}
